package press.lis.lise

import java.util

import com.twitter.Extractor
import com.typesafe.config.ConfigFactory
import com.typesafe.scalalogging.StrictLogging
import info.mukel.telegrambot4s.api.{Polling, TelegramBot}
import info.mukel.telegrambot4s.methods.{ParseMode, SendMessage}
import info.mukel.telegrambot4s.models._
import press.lis.lise.model.MessageDao

import scala.collection.JavaConversions._
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

  override def token = ConfigFactory.load.getString("bot.token")

  override def handleMessage(message: Message): Unit = {
    logger.trace(s"Message received: $message")

    try {
      message.text match {
        case Some("/getall") =>
          messageDao.readMessages(message.chat.id)
            .onComplete({
              case Success(messageList) =>
                val messages = messageList.mkString(";\n- ")

                logger.debug(s"Returning all saved messages: $messages")
                api.request(SendMessage(Left(message.chat.id),
                  s"Your messages:\n- $messages.",
                  parseMode = Some(ParseMode.Markdown))).andThen(logFailRequest)

              case ex =>
                logger.warn("Failed to get messages: $ex")
            })

        case Some("/showtags") =>
          messageDao.getUserTags(message.chat.id)
            .onComplete({
              case Success (messageList) =>
                val buttons =
                  messageList
                    .grouped(3)
                    .map(tags => tags.map(tag => KeyboardButton(s"#$tag")))
                    .toSeq

                val markup = ReplyKeyboardMarkup(
                  buttons,
                  resizeKeyboard = Some(true))

                api.request(SendMessage(Left(message.chat.id), "Choose a tag to get notes",
                  replyMarkup = Some(markup)))
                  .andThen(logFailRequest)

              case ex =>
                logger.warn("Failed to get tags: $ex")
            })

        case Some(t) =>
          logger.debug(s"Saving message: $message")

          val text: String = message.text.get

          val hashtags: util.List[String] = extractor.extractHashtags(text)

          logger.info(s"Hashtags parsed: $hashtags")

          messageDao.writeMessage(message.chat.id, message.messageId, text, hashtags)

        case _ =>
          logger.warn(s"Not implemented for: $message")
      }
    } catch {
      case ex: Exception => logger.warn(s"Failed to process request: $ex")
    }
  }


  run()

  override def handleCallbackQuery(callbackQuery: CallbackQuery): Unit = {
    logger.info(s"You pressed: $callbackQuery")
  }
}