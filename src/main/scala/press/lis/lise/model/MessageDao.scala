package press.lis.lise.model

import com.typesafe.scalalogging.StrictLogging
import press.lis.lise.model.MessageDao.MessageDTO
import scalikejdbc._
import scalikejdbc.config._

import scala.concurrent.{ExecutionContext, Future, Promise}

/**
  * @author Aleksandr Eliseev
  */
object MessageDao {
  case class MessageDTO(id: Long, text: String)

  case object MessageDTO extends SQLSyntaxSupport[MessageDTO] {
    def apply(rs: WrappedResultSet) = new MessageDTO(
      rs.long("id"), rs.string("message"))
  }
}

class MessageDao(implicit executor: ExecutionContext) extends StrictLogging {

  DBs.setupAll()

  private val OPENED = 0
  private val REMOVED = 1

  def writeMessage(telegramChatId: Long, telegramMessageId: Long, text: String, hashTags: Iterable[String]) = {

    val p = Promise[Long]

    Future {
      DB localTx {
        implicit session =>
          val messageId =
            sql"""
           INSERT INTO messages (telegram_message_id, message)
                         VALUES ($telegramMessageId, $text)
        """.updateAndReturnGeneratedKey.apply()

          // SELECT OR INSERT
          // ;-- is a crutch around prepared statement http://stackoverflow.com/a/27609457
          val sourceId =
            sql"""
           WITH new_row AS (
             INSERT INTO sources (telegram_chat_id)
             SELECT $telegramChatId
             WHERE NOT EXISTS (SELECT * FROM sources WHERE telegram_chat_id = $telegramChatId)
             RETURNING *
           )
           SELECT * FROM new_row
           UNION
           SELECT * FROM sources WHERE telegram_chat_id = $telegramChatId;--
        """.updateAndReturnGeneratedKey("id").apply()

          sql"""
            INSERT INTO messages_sources (message_id, source_id)
                         VALUES ($messageId, $sourceId)
        """.update().apply()

          hashTags.toStream.distinct.foreach(hashTag => {
            // SELECT OR INSERT WITH CRUTCH AS BEFORE
            val tagId =
              sql"""
               WITH new_row AS (
                 INSERT INTO tags (name)
                 SELECT $hashTag
                 WHERE NOT EXISTS (SELECT * FROM tags WHERE NAME = $hashTag)
                 RETURNING *
               )
               SELECT * FROM new_row
               UNION
               SELECT * FROM tags WHERE name = $hashTag;--
            """.updateAndReturnGeneratedKey("id").apply()

            sql"""
            INSERT INTO messages_tags (message_id, tag_id)
                         VALUES ($messageId, $tagId)
          """.update().apply()
          })

          logger.debug(s"[$messageId] New message inserted")

          p success messageId
      }
    }

    p.future
  }

  def addTag(messageId: Long, hashTag: String) = {

    val p = Promise[Long]

    Future {
      DB localTx {
        implicit session =>
          // SELECT OR INSERT WITH CRUTCH AS BEFORE
          val tagId =
            sql"""
               WITH new_row AS (
                 INSERT INTO tags (name)
                 SELECT $hashTag
                 WHERE NOT EXISTS (SELECT * FROM tags WHERE NAME = $hashTag)
                 RETURNING *
               )
               SELECT * FROM new_row
               UNION
               SELECT * FROM tags WHERE name = $hashTag;--
            """.updateAndReturnGeneratedKey("id").apply()

          sql"""
            INSERT INTO messages_tags (message_id, tag_id)
                         SELECT $messageId, $tagId
                         WHERE NOT EXISTS
                         (SELECT * FROM messages_tags
                            WHERE message_id = $messageId AND tag_id = $tagId)
          """.update().apply()

          logger.debug(s"[$messageId] Hashtag added")

          p success tagId
      }
    }

    p.future
  }

  def readMessages(telegramChatId: Long): Future[List[MessageDTO]] = {
    val p = Promise[List[MessageDTO]]

    Future {
      DB readOnly { implicit session =>
        val messages: List[MessageDTO] =
          sql"""SELECT m.id, m.message FROM messages m
                   JOIN messages_sources ms ON m.id = ms.message_id
                   JOIN sources s ON ms.source_id = s.id
                   WHERE s.telegram_chat_id = $telegramChatId
                    AND m.status != $REMOVED"""
            .map(rs => MessageDTO(rs))
            .list
            .apply()

        logger.debug(s"Got messages: $messages")

        p success messages
      }

      p success List()
    }

    p.future
  }

  def getUserTags(telegramChatId: Long): Future[List[String]] = {
    val p = Promise[List[String]]

    Future {
      DB readOnly { implicit session =>
        val tags: List[String] =
          sql"""SELECT DISTINCT t.name FROM tags t
                   JOIN messages_tags mt ON t.id = mt.tag_id
                   JOIN messages m ON m.id = mt.message_id
                   JOIN messages_sources ms ON ms.message_id = mt.message_id
                   JOIN sources s ON s.id = ms.source_id
                   WHERE s.telegram_chat_id = $telegramChatId
                    AND m.status != $REMOVED"""
            .map(rs => rs.string("name"))
            .list
            .apply()

        logger.debug(s"Got tags: $tags")

        p success tags
      }

      p success List()
    }

    p.future
  }

  def getMessagesByTag(telegramChatId: Long, tag: String): Future[List[MessageDTO]] = {
    val p = Promise[List[MessageDTO]]

    Future {
      DB readOnly { implicit session =>
        val messages: List[MessageDTO] =
          sql"""SELECT DISTINCT m.id, m.message FROM messages m
                   JOIN messages_tags mt ON m.id = mt.message_id
                   JOIN tags t ON t.id = mt.tag_id
                   JOIN messages_sources ms ON m.id = ms.message_id
                   JOIN sources s ON s.id = ms.source_id
                   WHERE s.telegram_chat_id = $telegramChatId
                    AND t.name = $tag
                    AND m.status != $REMOVED"""
            .map(rs => MessageDTO(rs))
            .list
            .apply()

        logger.debug(s"Got messages: $messages")

        p success messages
      }

      p success List()
    }

    p.future
  }

  def getMessagesForToday(telegramChatId: Long): Future[List[MessageDTO]] = {
    val p = Promise[List[MessageDTO]]

    Future {
      DB readOnly { implicit session =>
        val messages: List[MessageDTO] =
          sql"""SELECT DISTINCT m.id, m.message FROM messages m
                   JOIN messages_sources ms ON m.id = ms.message_id
                   JOIN sources s ON s.id = ms.source_id
                   WHERE s.telegram_chat_id = $telegramChatId
                     AND m.timestamp > NOW() - INTERVAL '1 day'
                     AND m.status != $REMOVED
                   ORDER BY m.id"""
            .map(rs => MessageDTO(rs))
            .list
            .apply()

        logger.debug(s"Got messages: $messages")

        p success messages
      }

      p success List()
    }

    p.future
  }

  def removeMessage(messageId: Long) = {
    Future {
      DB localTx { implicit session =>
          sql"""
            UPDATE messages SET
              status = $REMOVED,
              status_change = DEFAULT
              WHERE id = $messageId
        """.update().apply()

        logger.debug(s"[$messageId] Message removed")
      }
    }
  }

  def restoreMessage(messageId: Long) = {
    Future {
      DB localTx { implicit session =>
          sql"""
            UPDATE messages SET
              status = $OPENED,
              status_change = DEFAULT
              WHERE id = $messageId
        """.update().apply()

        logger.debug(s"[$messageId] Message restored")
      }
    }
  }
}
