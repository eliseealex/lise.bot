package press.lis.lise

import akka.actor.{FSM, Props}
import com.typesafe.scalalogging.{Logger, StrictLogging}
import info.mukel.telegrambot4s.api.TelegramApiAkka
import info.mukel.telegrambot4s.methods.{ParseMode, SendMessage}
import info.mukel.telegrambot4s.models.{KeyboardButton, Message, ReplyKeyboardHide, ReplyKeyboardMarkup}
import press.lis.lise.MessageHandler._
import press.lis.lise.MessageHandlerRouter.KillMessageHandler
import press.lis.lise.MessageParser.{Command, HashTag, TextMessage}
import press.lis.lise.model.MessageDao

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future}
import scala.language.postfixOps
import scala.util.{Failure, Success, Try}


/**
  * @author Aleksandr Eliseev
  */
object MessageHandler {

  def props(chatId: Long, api: TelegramApiAkka, messageDao: MessageDao) =
    Props(new MessageHandler(chatId, api, messageDao))

  def logFailRequest(logger: Logger): PartialFunction[Try[Message], Unit] = {
    case Failure(x) => logger.warn(s"Request failed: $x")
  }

  def sendMessageTo(api: TelegramApiAkka, logFailRequest: PartialFunction[Try[Message], Unit])
                   (chatId: Long)(message: String)(implicit ec: ExecutionContext) =
    api.request(SendMessage(Left(chatId), message,
      parseMode = Some(ParseMode.HTML),
      replyMarkup = Some(ReplyKeyboardHide(hideKeyboard = true))))
      .andThen(logFailRequest)

  sealed trait BotStates

  sealed trait StateData

  case class WrittenMessage(id: Long, text: String, hastTags: Seq[String]) extends StateData

  case class WhatsNew(leftNew: List[String]) extends StateData

  case object Uninitialized extends StateData

  case object Idle extends BotStates

  case object MessageWritten extends BotStates

  case object MessageRemoved extends BotStates

  case object WhatsNew extends BotStates

  case object Dying extends BotStates

}

class MessageHandler(chatId: Long, api: TelegramApiAkka, messageDao: MessageDao) extends FSM[BotStates, StateData] with StrictLogging {

  startWith(Idle, Uninitialized)

  val logFailRequest = MessageHandler.logFailRequest(logger)

  val sendMessage: (String) => Future[Message] = MessageHandler.sendMessageTo(api, logFailRequest)(chatId)

  when(Dying, stateTimeout = 3 minutes) {
    case Event(StateTimeout, _) =>

      logger.warn(s"[$chatId] Rescheduling killer for")

      // Messages have at most once delivery. Guarantee eventual consistency.
      context.parent ! KillMessageHandler(chatId)

      goto(Dying)

    case Event(anything, _) =>
      // Rescheduling to process after restart
      context.parent ! anything

      stay
  }

  when(Idle, stateTimeout = 15 minutes) {
    case Event(StateTimeout, _) =>
      logger.debug(s"[$chatId] Idle timeout, killing actor")

      context.parent ! KillMessageHandler(chatId)

      goto(Dying)
  }

  when(MessageWritten, stateTimeout = 30 minutes) {
    case Event(HashTag(tag), message: WrittenMessage) =>

      logger.debug(s"[$chatId] Writing hastTag [$tag] to previous message [${message.id}]")

      messageDao.addTag(message.id, tag)

      sendMessage(s"Yup, #$tag added. You can add another one.")

      stay()

    case Event(Command("remove"), message: WrittenMessage) =>

      logger.debug(s"[$chatId] Deleting written message [${message.id}]")

      messageDao.removeMessage(message.id).andThen({
        case Success(_) =>
          sendMessage("Message removed. You can /restore it")
        case Failure(f) =>
          logger.warn(s"[${message.id}] Failed to remove message", f)
      })

      goto(MessageRemoved)
  }

  when(MessageRemoved, stateTimeout = 30 minutes) {
    case Event(Command("restore"), message: WrittenMessage) =>

      logger.debug(s"[$chatId] Restoring message [${message.id}]")

      messageDao.restoreMessage(message.id).andThen({
        case Success(_) =>
          sendMessage("Message restored. You still can add hashtags or /remove it one more time")
        case Failure(f) =>
          logger.warn(s"[${message.id}] Failed to remove message", f)
      })

      goto(MessageWritten)
  }

  whenUnhandled {

    case Event(Command("getall"), _) =>

      messageDao.readMessages(chatId)
        .onComplete({
          case Success(messageList) if messageList.isEmpty =>

            sendMessage(s"You have no messages")

          case Success(messageList) =>

            val messages = messageList.mkString(";\n- ")

            sendMessage(s"Your messages:\n- $messages.")

          case ex =>
            logger.warn(s"Failed to get messages: $ex")
        })

      goto(Idle)

    case Event(Command("showtags"), _) =>

      messageDao.getUserTags(chatId)
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

            api.request(SendMessage(Left(chatId), "Choose a tag to get notes",
              replyMarkup = Some(markup)))
              .andThen(logFailRequest)

          case ex =>
            logger.warn(s"Failed to get tags: $ex")
        })

      goto(Idle)

    case Event(Command("whatsnew"), _) =>

      messageDao.getMessagesForToday(chatId)
        .onComplete({
          case Success(messageList) if messageList.isEmpty =>

            sendMessage(s"You have no messages")

          case Success(messageList) =>

            val messages = messageList.mkString(";\n- ")

            sendMessage(s"Your today's messages:\n- $messages.")

          case ex =>
            logger.warn(s"Failed to get today's messages: $ex")
        })

      goto(Idle)

    case Event(HashTag(tag), _) =>

      messageDao.getMessagesByTag(chatId, tag)
        .onComplete({
          case Success(messageList) if messageList.isEmpty =>

            sendMessage(s"You have no messages")

          case Success(messageList) =>

            val messages = messageList.mkString(";\n- ")

            sendMessage(s"Your $tag messages:\n- $messages.")

          case ex =>
            logger.warn(s"[$chatId] Failed to get messages by tag [$tag]: $ex")
        })

      goto(Idle)

    case Event(Command(unknown), _) =>

      sendMessage(s"Sorry i don't understand '$unknown' =(")

      goto(Idle)

    case Event(TextMessage(telegramId, text, hashtags), _) =>

      messageDao.writeMessage(chatId, telegramId, text, hashtags).andThen({
        case Success(id) =>
          self ! WrittenMessage(id, text, hashtags)

        case Failure(ex) =>
          logger.error(s"[$chatId] Failed to process message write $telegramId:", ex)
      })

      stay

    case Event(message: WrittenMessage, _) =>

      logger.debug(s"[$chatId] Going to written message mode.")

      sendMessage("You can add additional hashtag or /remove message")

      goto(MessageWritten) using message


    case Event(StateTimeout, _) =>

      goto(Idle) using Uninitialized

    case _ =>
      logger.warn("Unknown message type")

      stay
  }
}
