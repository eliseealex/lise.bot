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
import press.lis.lise.model.MessageDao.MessageDTO

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext, Future}
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

  case class ReadingMessages(leftNew: List[MessageDTO]) extends StateData

  case object Idle extends BotStates

  case object MessageWritten extends BotStates

  case object MessageRemoved extends BotStates

  case object Reading extends BotStates

  case object Dying extends BotStates

}

class MessageHandler(chatId: Long, api: TelegramApiAkka, messageDao: MessageDao) extends FSM[BotStates, StateData] with StrictLogging {

  startWith(Idle, ReadingMessages(List()))

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

  when(Idle, stateTimeout = 3 hours) {
    case Event(StateTimeout, _) =>
      logger.debug(s"[$chatId] Idle timeout, killing actor")

      context.parent ! KillMessageHandler(chatId)

      goto(Dying)
  }

  when(MessageWritten, stateTimeout = 2 hours) {
    case Event(Command("next"), ReadingMessages(messages)) =>

      logger.debug(s"[$chatId] Going to read again")

      goto(Reading) using ReadingMessages(messages.tail)
  }

  when(Reading, stateTimeout = 2 hours) {
    case Event(Command("next"), ReadingMessages(messages)) =>

      logger.debug(s"[$chatId] Going to the next message")

      goto(Reading) using ReadingMessages(messages.tail)
  }

  when(MessageRemoved, stateTimeout = 2 hours) {
    case Event(Command("restore"), message: ReadingMessages) =>

      logger.debug(s"[$chatId] Restoring message [${message.leftNew.head.id}]")

      messageDao.restoreMessage(message.leftNew.head.id).andThen({
        case Success(_) =>
          sendMessage("Message restored. You still can add hashtags, /remove it or go to the /next")
        case Failure(f) =>
          logger.warn(s"[${message.leftNew.head.id}] Failed to remove message", f)
      })

      goto(MessageWritten)

    case Event(Command("next"), ReadingMessages(messages)) =>

      logger.debug(s"[$chatId] Going to read again")

      goto(Reading) using ReadingMessages(messages.tail)
  }

  onTransition {
    case _ -> Reading =>
      nextStateData match {
        case ReadingMessages(messages) =>

          sendMessage(messages.head.text)
              .andThen{
                case Success(_) =>
                  if (messages.size > 1) {
                    sendMessage(s"Use /next to read ${messages.size - 1} more messages, /remove it or add hashtag")
                  }
                  else {
                    sendMessage("You're great! It was your last message in this list you can /remove it, add hashtag" +
                      " or ask me to /showtags or /getall if you're brave enough.")
                  }
                case Failure(ex) =>
                  logger.warn("Failed to send message", ex)
              }



        case x =>

          logger.warn(s"Unknown state data in read state: $x")
      }

    case _ -> Idle =>
      messageDao.getMessagesForToday(chatId)
        .andThen {
          case Success(messages) if messages.nonEmpty =>
            sendMessage(s"May be it's time to see /whatsnew? Today you have ${messages.size} messages.")
          case _ =>
            sendMessage(s"Ask me to /showtags. Maybe you forgot something interesting?")
        }
  }

  whenUnhandled {

    case Event(Command("getall"), _) =>

      Try(Await.result(
        messageDao.readMessages(chatId), 5 seconds)) match {
        case Success(messageList) if messageList.isEmpty =>

          sendMessage(s"You have no messages")

          stay

        case Success(messageList) =>

          goto(Reading) using ReadingMessages(messageList)

        case Failure(ex) =>

          logger.warn(s"[$chatId] Failed to get new messages", ex)

          stay
      }

    case Event(Command("showtags"), _) =>

      messageDao.getUserTags(chatId)
        .onComplete({
          case Success(tags) if tags.isEmpty =>

            sendMessage(s"You have no tags")

          case Success(tags) =>
            val buttons =
              tags
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

      goto(Idle) using ReadingMessages(List())

    case Event(Command("whatsnew"), _) =>

      Try(Await.result(
        messageDao.getMessagesForToday(chatId), 5 seconds)) match {
        case Success(list) =>
          goto(Reading) using ReadingMessages(list)

        case Failure(x) =>
          logger.warn(s"[$chatId] Failed to get new messages")

          stay
      }

    case Event(Command("remove"), ReadingMessages(head :: tail)) =>

      logger.debug(s"[$chatId] Deleting written message [${head.id}]")

      messageDao.removeMessage(head.id).andThen({
        case Success(_) =>
          sendMessage("Message removed. You can /restore it or go the /next if you sure.")
        case Failure(f) =>
          logger.warn(s"[${head.id}] Failed to remove message", f)
      })

      goto(MessageRemoved)

    case Event(HashTag(tag), ReadingMessages(head :: tail)) =>

      logger.debug(s"[$chatId] Writing hastTag [$tag] to previous message [${head.id}]")

      messageDao.addTag(head.id, tag)

      sendMessage(s"Yup, #$tag added. You can add another one.")

      stay()

    case Event(HashTag(tag), _) =>

      Try(Await.result(
        messageDao.getMessagesByTag(chatId, tag), 5 seconds)) match {
        case Success(list) =>
          goto(Reading) using ReadingMessages(list)

        case Failure(x) =>
          logger.warn(s"[$chatId] Failed to obtain message by tag $tag")

          stay
      }

    case Event(Command("showmeyourstateplease"), _) =>

      sendMessage(s"I'm in $stateName:$stateData")

      stay

    case Event(Command("start"), _) =>

      sendMessage(s"Hi I'm Lise. I can keep your thoughts and organize them by #hashtags." +
        s"\n\nTry to play with me! Send me a message and add #hashtag." +
        s"\nUse /showtags to get list of tags and then messages for some tag.")

      stay

    case Event(Command(unknown), _) =>

      sendMessage(s"Sorry i don't understand '$unknown' =(")

      stay

    case Event(TextMessage(telegramId, text, hashtags), _) =>

      messageDao.writeMessage(chatId, telegramId, text, hashtags).andThen({
        case Success(id) =>
          self ! MessageDTO(id, text)

        case Failure(ex) =>
          logger.error(s"[$chatId] Failed to process message write $telegramId:", ex)
      })

      stay

    case Event(message: MessageDTO, reading: ReadingMessages) =>

      logger.debug(s"[$chatId] Going to written message mode.")

      sendMessage("Send hashtag to add it to message.\nYou can /remove it or go back to your list using /next")

      goto(MessageWritten) using ReadingMessages(message :: reading.leftNew)


    case Event(StateTimeout, _) =>

      goto(Idle)

    case _ =>
      logger.warn("Unknown message type")

      stay
  }
}
