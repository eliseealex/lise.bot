package press.lis.lise

import info.mukel.telegrambot4s.Implicits._
import info.mukel.telegrambot4s.api.{Polling, TelegramBot}
import info.mukel.telegrambot4s.methods.SendMessage
import info.mukel.telegrambot4s.models.Message

import scala.io.Source

/**
  *
  * @author Aleksandr Eliseev
  */
object Bot extends TelegramBot with Polling with App {
  override def token = Source.fromFile("lise.bot.token").getLines().next

  override def handleMessage(message: Message): Unit = {
    message.text.foreach(t => api.request(SendMessage(message.chat.id,
      s"I have received: $t")))
  }

  run()

}