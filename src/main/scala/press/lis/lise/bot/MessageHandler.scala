package press.lis.lise.bot

import java.util.Objects

import akka.actor.{ActorRef, Props}
import akka.persistence.fsm.PersistentFSM
import akka.persistence.fsm.PersistentFSM.FSMState
import com.typesafe.scalalogging.{Logger, StrictLogging}
import info.mukel.telegrambot4s.api.TelegramApiAkka
import info.mukel.telegrambot4s.methods.{ParseMode, SendMessage}
import info.mukel.telegrambot4s.models.{KeyboardButton, Message, ReplyKeyboardHide, ReplyKeyboardMarkup}
import press.lis.lise.bot.MessageHandler._
import press.lis.lise.bot.MessageHandlerRouter.KillMessageHandler
import press.lis.lise.bot.MessageParser.{Command, HashTag, TextMessage}
import press.lis.lise.bot.MessageScheduler.{SendState, Snooze, SnoozedMessage}
import press.lis.lise.model.MessageDao
import press.lis.lise.model.MessageDao.MessageDTO

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext, Future}
import scala.language.postfixOps
import scala.reflect.ClassTag
import scala.util.{Failure, Success, Try}


/**
  * Uses akka persistent fsm
  *
  * @author Aleksandr Eliseev
  */
object MessageHandler {

  def props(chatId: Long, api: TelegramApiAkka, messageDao: MessageDao, messageScheduler: ActorRef) =
    Props(new MessageHandler(chatId, api, messageDao, messageScheduler))

  def logFailRequest(logger: Logger): PartialFunction[Try[Message], Unit] = {
    case Failure(x) => logger.warn(s"Request failed: $x")
  }

  def sendMessageTo(api: TelegramApiAkka, logFailRequest: PartialFunction[Try[Message], Unit])
                   (chatId: Long)(replyToMessageId: Option[Long])(message: String)(implicit ec: ExecutionContext) =
    api.request(SendMessage(Left(chatId), message,
      parseMode = Some(ParseMode.HTML),
      replyMarkup = Some(ReplyKeyboardHide(hideKeyboard = true)),
      replyToMessageId = replyToMessageId))
      .andThen(logFailRequest)

  sealed trait BotStates extends FSMState

  sealed trait StateData

  sealed trait BotEvent

  case object Next extends BotEvent

  case class NewList(newList: List[MessageDTO]) extends BotEvent

  case class AddMessage(newMessage: MessageDTO) extends BotEvent

  case class ReadingMessages(leftNew: List[MessageDTO]) extends StateData

  case object Idle extends BotStates {
    override def identifier: String = "Idle"
  }

  case object MessageWritten extends BotStates {
    override def identifier: String = "Message Written"
  }

  case object MessageRemoved extends BotStates {
    override def identifier: String = "Message Removed"
  }

  case object Reading extends BotStates {
    override def identifier: String = "Reading"
  }

  case object Dying extends BotStates {
    override def identifier: String = "Dying"
  }

}

class MessageHandler(chatId: Long,
                     api: TelegramApiAkka,
                     messageDao: MessageDao,
                     messageScheduler: ActorRef)(implicit val domainEventClassTag: ClassTag[BotEvent])
  extends PersistentFSM[BotStates, StateData, BotEvent]
    with StrictLogging {

  startWith(Idle, ReadingMessages(List()))

  val logFailRequest = MessageHandler.logFailRequest(logger)

  val sendMessage: (String) => Future[Message] =
    MessageHandler.sendMessageTo(api, logFailRequest)(chatId)(replyToMessageId = None)

  override def persistenceId: String = s"MessageHandler-$chatId"

  // TODO Reply still cool, but i should carefully think how to use it.
  @Deprecated
  def sendReply(messageId: Long, message: String) =
  MessageHandler.sendMessageTo(api, logFailRequest)(chatId)(Some(messageId))(message)

  override def applyEvent(domainEvent: BotEvent, currentData: StateData): StateData = currentData match {
    case ReadingMessages(messages) =>
      domainEvent match {
        case Next =>
          ReadingMessages(messages.tail)
        case NewList(newMessages) =>
          ReadingMessages(newMessages)
        case AddMessage(message) =>
          ReadingMessages(message :: messages)
      }

    case x =>
      logger.warn(s"Unknown data type: $x")
      x
  }

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

  when(Idle, stateTimeout = 2 hours) {
    case Event(Command("next"), ReadingMessages(head :: tail)) if tail != Nil =>

      logger.debug(s"[$chatId] Going to read again")

      goto(Reading) applying Next
  }

  when(MessageWritten, stateTimeout = 2 hours) {
    case Event(Command("next"), ReadingMessages(head :: tail)) if tail != Nil =>

      logger.debug(s"[$chatId] Going to read again")

      goto(Reading) applying Next
  }

  when(Reading, stateTimeout = 2 hours) {
    case Event(Command("next"), ReadingMessages(head :: tail)) if tail != Nil =>

      logger.debug(s"[$chatId] Going to the next message")

      goto(Reading) applying Next
  }

  when(MessageRemoved, stateTimeout = 12 hours) {
    case Event(Command("restore"), message: ReadingMessages) =>

      logger.debug(s"[$chatId] Restoring message [${message.leftNew.head.id}]")

      messageDao.restoreMessage(message.leftNew.head.id).andThen({
        case Success(_) =>
          sendMessage("Message restored. You can snooze it for (/15min, /hour, /4hours, /day), /addtag, " +
            "/remove it or go to the /next")
        case Failure(f) =>
          logger.warn(s"[${message.leftNew.head.id}] Failed to remove message", f)
      })

      goto(MessageWritten)

    case Event(Command("next"), ReadingMessages(head :: tail)) if tail != Nil =>

      logger.debug(s"[$chatId] Going to read again")

      goto(Reading) applying Next
  }

  onTransition {
    case _ -> Reading =>
      nextStateData match {
        case ReadingMessages(messages) =>

          sendMessage(messages.head.text)
            .andThen {
              case Success(_) =>
                if (messages.size > 1) {
                  sendMessage(s"Use /next to read ${messages.size - 1} more messages, snooze it " +
                    s"for (/15min, /hour, /4hours, /day), /remove it or /addtag")
                }
                else {
                  sendMessage("You're great! It was your last message in this list you can" +
                    " snooze it for (/15min, /hour, /4hours, /day), /remove it, /addtag" +
                    " or ask me for /messagesfortag or /getall if you're brave enough.")
                }
              case Failure(ex) =>
                logger.warn("Failed to send message", ex)
            }


        case x =>

          logger.warn(s"Unknown state data in read state: $x")
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

          goto(Reading) applying NewList(messageList)

        case Failure(ex) =>

          logger.warn(s"[$chatId] Failed to get new messages", ex)

          stay
      }

    case Event(Command("messagesfortag"), _) =>

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

            api.request(SendMessage(Left(chatId), "Choose a tag or send you own",
              replyMarkup = Some(markup)))
              .andThen(logFailRequest)

          case ex =>
            logger.warn(s"Failed to get tags: $ex")
        })

      stay() applying NewList(List())

    case Event(Command("lastday"), _) =>

      Try(Await.result(
        messageDao.getMessages(chatId, 1), 30 seconds)) match {
        case Success(list) =>
          goto(Reading) applying NewList(list)

        case Failure(x) =>
          logger.warn(s"[$chatId] Failed to get new messages")

          stay
      }

    case Event(Command("lastweek"), _) =>

      Try(Await.result(
        messageDao.getMessages(chatId, 7), 30 seconds)) match {
        case Success(list) =>
          goto(Reading) applying NewList(list)

        case Failure(x) =>
          logger.warn(s"[$chatId] Failed to get new messages")

          stay
      }

    case Event(Command("remove"), ReadingMessages(head :: tail)) =>

      logger.debug(s"[$chatId] Deleting written message [${head.id}]")

      sendMessage(head.text)

      messageDao.removeMessage(head.id).andThen({
        case Success(_) =>
          sendMessage("Message removed. You can /restore it or go the /next if you sure.")
        case Failure(f) =>
          logger.warn(s"[${head.id}] Failed to remove message =(", f)
      })

      goto(MessageRemoved)

    case Event(Command("addtag"), ReadingMessages(head :: tail)) =>

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

            api.request(SendMessage(Left(chatId), "Choose a tag to add to note",
              replyMarkup = Some(markup)))
              .andThen(logFailRequest)

          case ex =>
            logger.warn(s"Failed to get tags: $ex")
        })

      stay

    case Event(Command("snooze"), ReadingMessages(head :: tail)) =>

      logger.debug(s"[$chatId] Snoozing message [${head.id}]")

      sendMessage(head.text)
        .andThen {
          case Success(_) =>
            sendMessage(s"I will remind you about this message soon")
          case Failure(ex) =>
            logger.warn("Failed to send message", ex)
        }

      messageScheduler ! Snooze(SnoozedMessage(head.telegramId, chatId, head), 1 hour)

      stay

    case Event(Command("10sec"), ReadingMessages(head :: tail)) =>

      logger.debug(s"[$chatId] Snoozing message [${head.id}] for 10 seconds")

      sendMessage(head.text)
        .andThen {
          case Success(_) =>
            sendMessage(s"I will remind you about this message in ten seconds")
          case Failure(ex) =>
            logger.warn("Failed to send message", ex)
        }

      messageScheduler ! Snooze(SnoozedMessage(head.telegramId, chatId, head), 10 seconds)

      stay

    case Event(Command("15min"), ReadingMessages(head :: tail)) =>

      logger.debug(s"[$chatId] Snoozing message [${head.id}] for 15 minutes")

      sendMessage(head.text)
        .andThen {
          case Success(_) =>
            sendMessage(s"I will remind you about this message in fifteen minutes")
          case Failure(ex) =>
            logger.warn("Failed to send message", ex)
        }

      messageScheduler ! Snooze(SnoozedMessage(head.telegramId, chatId, head), 15 minutes)

      stay

    case Event(Command("hour"), ReadingMessages(head :: tail)) =>

      logger.debug(s"[$chatId] Snoozing message [${head.id}] for an hour")

      sendMessage(head.text)
        .andThen {
          case Success(_) =>
            sendMessage(s"I will remind you about this message in an hour")
          case Failure(ex) =>
            logger.warn("Failed to send message", ex)
        }

      messageScheduler ! Snooze(SnoozedMessage(head.telegramId, chatId, head), 1 hour)

      stay

    case Event(Command("4hours"), ReadingMessages(head :: tail)) =>

      logger.debug(s"[$chatId] Snoozing message [${head.id}] for 4 hours")

      sendMessage(head.text)
        .andThen {
          case Success(_) =>
            sendMessage(s"I will remind you about this message in four hours")
          case Failure(ex) =>
            logger.warn("Failed to send message", ex)
        }

      messageScheduler ! Snooze(SnoozedMessage(head.telegramId, chatId, head), 4 hour)

      stay

    case Event(Command("day"), ReadingMessages(head :: tail)) =>

      logger.debug(s"[$chatId] Snoozing message [${head.id}] for a day")

      sendMessage(head.text)
        .andThen {
          case Success(_) =>
            sendMessage(s"I will remind you about this message in a day")
          case Failure(ex) =>
            logger.warn("Failed to send message", ex)
        }

      messageScheduler ! Snooze(SnoozedMessage(head.telegramId, chatId, head), 1 day)


      stay

    case Event(HashTag(tag), ReadingMessages(head :: tail)) =>

      logger.debug(s"[$chatId] Writing hastTag [$tag] to previous message [${head.id}]")

      messageDao.addTag(head.id, tag)

      sendMessage(s"Yup, #$tag added. You can add another one using /addtag.")

      stay

    case Event(HashTag(tag), _) =>

      Try(Await.result(
        messageDao.getMessagesByTag(chatId, tag), 5 seconds)) match {
        case Success(list) =>
          goto(Reading) applying NewList(list)

        case Failure(x) =>
          logger.warn(s"[$chatId] Failed to obtain message by tag $tag")

          stay
      }

    case Event(Command("showmeyourstateplease"), _) =>

      sendMessage(s"I'm in $stateName:$stateData")

      messageScheduler ! SendState(sendMessage)

      stay

    case Event(Command("start"), _) =>

      sendMessage(s"Hi I'm Lise. I can keep your thoughts and organize them by #hashtags." +
        s"\n\nTry to play with me! Send me a message and add #hashtag." +
        s"\nUse /messagesfortag to get list of tags and then messages for some tag.")

      stay

    case Event(Command("next"), _) =>
      sendMessage("You've already read all your messages. Use /lastday, /lastweek, /getall or /messagesfortag " +
        "to get new list")

      stay

    case Event(Command(unknown), _) =>

      sendMessage(s"Sorry i don't understand '$unknown' =(")

      stay

    case Event(MessageScheduler.SnoozedMessage(telegramMessageId, _, messageDTO), ReadingMessages(existingMessages)) =>

      sendMessage(s"You ask me to remind you about this message. " +
        s"/remove it if you've done it or snooze it for (/15min, /hour, /4hours, /day):")
        .andThen({
          case Success(id) =>
            sendMessage(messageDTO.text)

          case Failure(ex) =>
            logger.warn(s"[$chatId] Failed to send message [$messageDTO]")
        })

      val messageRemoved = existingMessages.filterNot(message => Objects.equals(message.id, messageDTO.id))

      goto(Idle) applying NewList(messageDTO :: messageRemoved)

    case Event(TextMessage(telegramId, text, hashtags), _) =>

      messageDao.writeMessage(chatId, telegramId, text, hashtags).andThen({
        case Success(id) =>
          self ! MessageDTO(id, telegramId, text)

        case Failure(ex) =>
          logger.error(s"[$chatId] Failed to process message write $telegramId:", ex)
      })

      stay

    case Event(message: MessageDTO, reading: ReadingMessages) =>

      logger.debug(s"[$chatId] Going to written message mode.")

      sendMessage("Use /addtag to add tag.\nYou can snooze it for (/15min, /hour, /4hours, /day), /remove it" +
        " or go back to your list using /next")

      goto(MessageWritten) applying AddMessage(message)


    case Event(StateTimeout, _) =>
      logger.debug(s"[$chatId] Timeout, killing actor")

      saveStateSnapshot()

      context.parent ! KillMessageHandler(chatId)

      goto(Dying)

    case _ =>
      logger.warn("Unknown message type")

      stay
  }
}
