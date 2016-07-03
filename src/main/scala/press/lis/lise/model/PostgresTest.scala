package press.lis.lise.model

import com.typesafe.scalalogging.StrictLogging
import scalikejdbc._

/**
  * @author Aleksandr Eliseev
  */
object PostgresTest extends App with StrictLogging {
  Class.forName("org.postgresql.Driver")

  ConnectionPool.singleton("jdbc:postgresql://localhost:5432/lise", "lise_root", "root")


  DB readOnly { implicit session =>
    val messages = sql"""select * from messages m
         join messages_sources ms on m.id = ms.message_id
         join sources s ON ms.source_id = s.id
         WHERE s.telegram_chat_id = '42'""".map(_.toMap()).list.apply()

    logger.info(s"Received messages: $messages")
  }
}
