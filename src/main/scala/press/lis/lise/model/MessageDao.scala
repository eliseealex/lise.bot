package press.lis.lise.model

import com.typesafe.scalalogging.StrictLogging
import scalikejdbc._

import scala.concurrent.{ExecutionContext, Future, Promise}

/**
  * @author Aleksandr Eliseev
  */
class MessageDao(implicit executor: ExecutionContext) extends StrictLogging {
  Class.forName("org.postgresql.Driver")

  ConnectionPool.singleton("jdbc:postgresql://localhost:5432/lise", "lise_root", "root")

  def writeMessage(telegramChatId: Long, telegramMessageId: Long, text: String, hashTags: Iterable[String]) =
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
                 WHERE NOT EXISTS (SELECT * FROM tags WHERE name = $hashTag)
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
    }

  def readMessages(telegramChatId: Long): Future[List[String]] = {
    val p = Promise[List[String]]

    Future {
      DB readOnly { implicit session =>
        val messages: List[String] = sql"""select m.message from messages m
       join messages_sources ms on m.id = ms.message_id
       join sources s ON ms.source_id = s.id
       WHERE s.telegram_chat_id = $telegramChatId""".map(_.string("message"))
          .list
          .apply()

        logger.debug(s"Got messages: $messages")

        p success messages
      }

      p success List()
    }

    p.future
  }
}