package press.lis.lise.model

import com.typesafe.scalalogging.StrictLogging
import scalikejdbc._

/**
  * @author Aleksandr Eliseev
  */
class MessageDao extends StrictLogging {
  Class.forName("org.postgresql.Driver")

  ConnectionPool.singleton("jdbc:postgresql://localhost:5432/lise", "lise_root", "root")

  def writeMessage(telegramChatId: Long, telegramMessageId: Long, text: String) =
    writeMessage(String.valueOf(telegramChatId), String.valueOf(telegramMessageId), text)

  def writeMessage(telegramChatId: String, telegramMessageId: String, text: String) =
    DB localTx {
      implicit session =>
        // SELECT OR INSERT
        // ;-- is a crutch around prepared statement http://stackoverflow.com/a/27609457
        val sourceId =
          sql"""
           WITH new_row AS (
             INSERT INTO sources (telegram_chat_id)
             SELECT ${telegramChatId}
             WHERE NOT EXISTS (SELECT * FROM sources WHERE telegram_chat_id = ${telegramChatId})
             RETURNING *
           )
           SELECT * FROM new_row
           UNION
           SELECT * FROM sources WHERE telegram_chat_id = ${telegramChatId};--
        """.updateAndReturnGeneratedKey("id").apply()

        val messageId =
          sql"""
           INSERT INTO messages (telegram_message_id, message)
                         VALUES (${telegramMessageId}, ${text})
        """.updateAndReturnGeneratedKey.apply()

        sql"""
            INSERT INTO messages_sources (message_id, source_id)
                         VALUES (${messageId}, ${sourceId})
        """.update().apply()

        logger.debug(s"Insert of message succeed. Source id: $sourceId. Message id: $messageId")
    }
}
