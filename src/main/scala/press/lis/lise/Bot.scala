package press.lis.lise

import com.typesafe.scalalogging.StrictLogging
import info.mukel.telegrambot4s.api.{Polling, TelegramBot}
import info.mukel.telegrambot4s.methods.{ParseMode, SendMessage}
import info.mukel.telegrambot4s.models.Message
import press.lis.lise.model.MessageDao

import scala.io.Source
import scala.util.{Failure, Success, Try}

/**
  *
  * @author Aleksandr Eliseev
  */
object Bot extends TelegramBot with Polling with App with StrictLogging {
  override def token = Source.fromFile("lise.bot.token").getLines().next

  val messageDao = new MessageDao

  val logFailRequest: PartialFunction[Try[Message], Unit] = {
    case Failure(x) => logger.warn(s"Request failed: $x")
  }

  override def handleMessage(message: Message): Unit = {
    logger.trace(s"Message received: $message")

    message.text match {
      case Some("/getAll") =>
        messageDao.readMessages(message.chat.id)
          .onComplete({
            case Success(messageList) =>
              val messages = messageList.mkString(";\n- ")

              logger.debug(s"Returning all saved messages: $messages")
              api.request(SendMessage(Left(message.chat.id),
                s"Your messages:\n- $messages.",
                parseMode = Some(ParseMode.Markdown))).andThen(logFailRequest)

            case ex =>
              logger.warn("Got exception: $ex")
          })

      case Some(t) =>
        logger.debug(s"Saving message: $message")
        messageDao.writeMessage(message.chat.id, message.messageId, message.text.get)

      case _ =>
        logger.warn(s"Not implemented for: $message")
    }
  }

  run()

}