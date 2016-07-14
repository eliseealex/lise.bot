package press.lis.lise

import com.typesafe.config.ConfigFactory
import com.typesafe.scalalogging.StrictLogging
import info.mukel.telegrambot4s.api.{Polling, TelegramBot}
import info.mukel.telegrambot4s.methods.{ParseMode, SendMessage}
import info.mukel.telegrambot4s.models._
import press.lis.lise.MessageParser._
import press.lis.lise.model.MessageDao

import scala.concurrent.Future
import scala.util.{Failure, Success, Try}

/**
  *
  * @author Aleksandr Eliseev
  */
object Bot extends TelegramBot with Polling with App with StrictLogging {

  val messageDao = new MessageDao

  val messageParser = MessageParser

  val logFailRequest: PartialFunction[Try[Message], Unit] = {
    case Failure(x) => logger.warn(s"Request failed: $x")
  }

  override def token = ConfigFactory.load.getString("bot.token")

  override def handleMessage(message: Message): Unit = {
    logger.trace(s"Message received: $message")

    val sendMessage: (String) => Future[Message] = sendMessageTo(message.chat.id)

    val botMessage: BotMessage = messageParser.parse(message.text)

    logger.debug(s"[${message.chat.id}] Accepted message: $botMessage")

    try
      botMessage match {
        case _ if message.chat.id < 0 =>

          sendMessage("Sorry, I can't work in group chat yet. Try to talk to me personally.")

        case Command("getall") =>

          messageDao.readMessages(message.chat.id)
            .onComplete({
              case Success(messageList) if messageList.isEmpty =>

                sendMessage(s"You have no messages")

              case Success(messageList) =>

                val messages = messageList.mkString(";\n- ")

                sendMessage(s"Your messages:\n- $messages.")

              case ex =>
                logger.warn(s"Failed to get messages: $ex")
            })

        case Command("showtags") =>

          messageDao.getUserTags(message.chat.id)
            .onComplete({
              case Success(messageList) if messageList.isEmpty =>

                sendMessage(s"You have no messages")

              case Success(messageList) =>
                val buttons =
                  messageList
                    .grouped(2)
                    .map(tags => tags.map(tag => KeyboardButton(s"#$tag")))
                    .toSeq

                val markup = ReplyKeyboardMarkup(
                  buttons,
                  resizeKeyboard = Some(true),
                  oneTimeKeyboard = Some(true))

                api.request(SendMessage(Left(message.chat.id), "Choose a tag to get notes",
                  replyMarkup = Some(markup)))
                  .andThen(logFailRequest)

              case ex =>
                logger.warn(s"Failed to get tags: $ex")
            })

        case Command("whatsnew") =>

          messageDao.getMessagesForToday(message.chat.id)
            .onComplete({
              case Success(messageList) if messageList.isEmpty =>

                sendMessage(s"You have no messages")

              case Success(messageList) =>

                val messages = messageList.mkString(";\n- ")

                sendMessage(s"Your today's messages:\n- $messages.")

              case ex =>
                logger.warn(s"Failed to get today's messages: $ex")
            })

        case HashTag(tag) =>

          messageDao.getMessagesByTag(message.chat.id, tag)
            .onComplete({
              case Success(messageList) if messageList.isEmpty =>

                sendMessage(s"You have no messages")

              case Success(messageList) =>

                val messages = messageList.mkString(";\n- ")

                sendMessage(s"Your $tag messages:\n- $messages.")

              case ex =>
                logger.warn(s"Failed to get messages by tag [$tag]: $ex")
            })

        case Command(unknown) =>

          sendMessage(s"Sorry i don't understand '$unknown' =(")

        case TextMessage(text, hashtags) =>

          messageDao.writeMessage(message.chat.id, message.messageId, text, hashtags)

        case Unknown =>

          logger.warn(s"Not implemented for: $message")
      }
    catch {
      case ex: Exception => logger.warn(s"Failed to process request: $ex")
    }
  }

  def sendMessageTo(chatId: Long)(message: String) =
    api.request(SendMessage(Left(chatId), message,
      parseMode = Some(ParseMode.Markdown),
      replyMarkup = Some(ReplyKeyboardHide(hideKeyboard = true))))
      .andThen(logFailRequest)


  run()
}