package press.lis.lise

import akka.actor.{Actor, ActorRef, Kill, Props}
import com.typesafe.scalalogging.StrictLogging
import info.mukel.telegrambot4s.api.TelegramApiAkka
import info.mukel.telegrambot4s.methods.{ParseMode, SendMessage}
import info.mukel.telegrambot4s.models.{Message, ReplyKeyboardHide}
import press.lis.lise.MessageHandlerRouter.{KillMessageHandler, Reschedule}
import press.lis.lise.MessageParser.BotMessage
import press.lis.lise.model.MessageDao

import scala.collection.mutable
import scala.concurrent.ExecutionContext.Implicits.global
import scala.util.Failure

/**
  * @author Aleksandr Eliseev
  */
object MessageHandlerRouter {
  final case class KillMessageHandler(chatId: Long)

  final case class Reschedule(chatId: Long, message: Any)

  def props(api: TelegramApiAkka, messageDao: MessageDao) =
    Props(new MessageHandlerRouter(api, messageDao))
}

class MessageHandlerRouter(api: TelegramApiAkka, messageDao: MessageDao)
  extends Actor with StrictLogging {

  val messageParser = MessageParser

  val messageHandlers = mutable.HashMap[Long, ActorRef]()

  val messageScheduler = context.actorOf(MessageScheduler.props(self))

  override def receive: Receive = {
    case message: Message if message.chat.id < 0 =>

      api.request(SendMessage(Left(message.chat.id),
        "Sorry, I can't work in group chat yet. Try to talk to me personally.",
        parseMode = Some(ParseMode.Markdown),
        replyMarkup = Some(ReplyKeyboardHide(hideKeyboard = true))))
        .andThen {
          case Failure(x) => logger.warn(s"Request failed: $x")
        }

    case message: Message =>

      logger.trace(s"Parsing message [$message]")

      val chatId: Long = message.chat.id

      val botMessage: BotMessage = messageParser.parse(message.text, message.messageId)

      logger.debug(s"[$chatId] Accepted message: $botMessage")

      val messageHandler =
        messageHandlers.getOrElseUpdate(chatId,
          context.actorOf(MessageHandler.props(chatId, api, messageDao, messageScheduler)))

      messageHandler ! botMessage

    case KillMessageHandler(chatId) =>

      messageHandlers.remove(chatId)
        .foreach(actorRef => actorRef ! Kill)

    case Reschedule(chatId, message) =>

      val messageHandler =
        messageHandlers.getOrElseUpdate(chatId,
          context.actorOf(MessageHandler.props(chatId, api, messageDao, messageScheduler)))

      messageHandler ! message

    case message @ MessageScheduler.SnoozedMessage(_, chatId, _) =>

      val messageHandler =
        messageHandlers.getOrElseUpdate(chatId,
          context.actorOf(MessageHandler.props(chatId, api, messageDao, messageScheduler)))

      messageHandler ! message

    case _ =>
      logger.warn("Use Message in router!")
  }
}
