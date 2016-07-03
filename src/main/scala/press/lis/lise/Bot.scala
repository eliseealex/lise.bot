package press.lis.lise

import com.typesafe.scalalogging.StrictLogging
import info.mukel.telegrambot4s.api.{Polling, TelegramBot}
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
    logger.debug(s"Message received: $message")

    messageDao.writeMessage(message.chat.id, message.messageId, message.text.get)
  }

  run()

}