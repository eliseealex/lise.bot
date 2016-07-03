package press.lis.lise.model

import com.typesafe.scalalogging.StrictLogging
import scalikejdbc._

/**
  * @author Aleksandr Eliseev
  */
object PostgresTest extends App with StrictLogging {
  Class.forName("org.postgresql.Driver")

  ConnectionPool.singleton("jdbc:postgresql://localhost:5432/lise", "lise_root", "root")


  DB localTx {
    implicit session =>
      // SELECT OR INSERT
      // ;-- is a crutch around prepared statement http://stackoverflow.com/a/27609457
      val sourceId = sql"""
           WITH new_row AS (
             INSERT INTO sources (telegram_chat_id)
             SELECT '42'
             WHERE NOT EXISTS (SELECT * FROM sources WHERE telegram_chat_id = '42')
             RETURNING *
           )
           SELECT * FROM new_row
           UNION
           SELECT * FROM sources WHERE telegram_chat_id = '42';--
        """.updateAndReturnGeneratedKey("id").apply()

      val messageId = sql"""
           INSERT INTO messages (telegram_message_id, message)
                         VALUES ('44', 'test')
        """.updateAndReturnGeneratedKey.apply()

      sql"""
            INSERT INTO messages_sources (message_id, source_id)
                         VALUES (${messageId}, ${sourceId})
        """.update().apply()

      logger.info(s"Insert of message succeed. Source id: $sourceId. Message id: $messageId")
  }
}
