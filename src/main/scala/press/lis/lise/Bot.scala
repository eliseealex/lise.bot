package press.lis.lise

import java.util

import com.twitter.Extractor
import com.typesafe.scalalogging.StrictLogging
import info.mukel.telegrambot4s.api.{Polling, TelegramBot}
import info.mukel.telegrambot4s.methods.{ParseMode, SendMessage}
import info.mukel.telegrambot4s.models.{CallbackQuery, InlineKeyboardButton, InlineKeyboardMarkup, Message}
import press.lis.lise.model.MessageDao

import scala.collection.JavaConversions._
import scala.io.Source
import scala.util.{Failure, Success, Try}

/**
  *
  * @author Aleksandr Eliseev
  */
object Bot extends TelegramBot with Polling with App with StrictLogging {
  val messageDao = new MessageDao
  val extractor = new Extractor
  val logFailRequest: PartialFunction[Try[Message], Unit] = {
    case Failure(x) => logger.warn(s"Request failed: $x")
  }

  override def token = Source.fromFile("lise.bot.token").getLines().next

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

      case Some("/showTags") =>
        val markup = InlineKeyboardMarkup(
          Seq(Seq(InlineKeyboardButton("test1", callbackData = Some("t")),
            InlineKeyboardButton("test3", callbackData = Some("f"))),
            Seq(InlineKeyboardButton("test2", callbackData = Some("p")))))

        api.request(SendMessage(Left(message.chat.id), "test",
          replyMarkup = Some(markup)))
          .andThen(logFailRequest)

      case Some(t) =>
        logger.debug(s"Saving message: $message")

        val text: String = message.text.get

        val hashtags: util.List[String] = extractor.extractHashtags(text)

        logger.info(s"Hashtags parsed: $hashtags")

        messageDao.writeMessage(message.chat.id, message.messageId, text, hashtags)

      case _ =>
        logger.warn(s"Not implemented for: $message")
    }
  }


  run()

  override def handleCallbackQuery(callbackQuery: CallbackQuery): Unit = {
    logger.info(s"You pressed: $callbackQuery")
  }
}