package press.lis.lise.model

import com.typesafe.scalalogging.StrictLogging
import slick.driver.PostgresDriver.api._

import scala.concurrent.{ExecutionContext, Future}

/**
  * @author Aleksandr Eliseev
  */
class MessageDao(db: Database)(implicit ec: ExecutionContext) extends StrictLogging{
  def createMessage(telegramChatId: String, telegramMessageId: String, text: String) = {
    for {
      sourceId <- db.run((Model.sources returning Model.sources.map(_.id)) +=
        Model.Source(None, telegramChatId))
      messageId <- db.run((Model.messages returning Model.messages.map(_.id)) +=
        Model.Message(None, telegramMessageId, text))
    } yield {
      val run: Future[Int] = db.run(Model.messagesSources += Model.MessageToSource(messageId, sourceId))


      run.onComplete({
        case x => logger.info(s"$x")
      })
    }
  }
}

object MessageDao extends App{

  val db = Database.forConfig("lisedb")

  implicit val ec = ExecutionContext.global


  try {
    val dao: MessageDao = new MessageDao(db)
    dao.createMessage("411111", "4211", "4311")
  } finally db.close()
}