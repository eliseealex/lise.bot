package press.lis.lise.bot

import com.typesafe.config.ConfigFactory
import com.typesafe.scalalogging.StrictLogging
import info.mukel.telegrambot4s.api.{Polling, TelegramBot}
import info.mukel.telegrambot4s.models._
import press.lis.lise.TestServer
import press.lis.lise.model.MessageDao

/**
  *
  * @author Aleksandr Eliseev
  */
object Bot extends App with TelegramBot with Polling with StrictLogging {

  val messageDao = new MessageDao

  val messageParser = MessageParser

  val messageHandlerRouter = system.actorOf(MessageHandlerRouter.props(api, messageDao))

  system.actorOf(TestServer.props())

  override def token = ConfigFactory.load.getString("bot.token")

  override def handleMessage(message: Message): Unit = {
    logger.trace(s"Message received: $message")

    messageHandlerRouter ! message
  }

  run()
}