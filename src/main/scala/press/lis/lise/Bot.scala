package press.lis.lise

import com.typesafe.scalalogging.StrictLogging
import info.mukel.telegrambot4s.api.{Polling, TelegramBot}
import info.mukel.telegrambot4s.methods.SendMessage
import info.mukel.telegrambot4s.models.Message
import press.lis.lise.model.MessageDao

import scala.io.Source

/**
  *
  * @author Aleksandr Eliseev
  */
object Bot extends TelegramBot with Polling with App with StrictLogging {
  override def token = Source.fromFile("lise.bot.token").getLines().next

  val messageDao = new MessageDao

  override def handleMessage(message: Message): Unit = {
    logger.trace(s"Message received: $message")

    message.text match {
      case Some("/getAll") =>
        logger.debug("Returning all saved messages")
        api.request(SendMessage(Left(message.chat.id), s"Coming soon"))
      case Some(t) =>
        logger.debug(s"Saving message: $message")
        messageDao.writeMessage(message.chat.id, message.messageId, message.text.get)
      case _ =>
        logger.warn(s"Not implemented for: $message")
    }
  }

  run()

}