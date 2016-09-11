package press.lis.lise

import java.util

import akka.actor.{Actor, ActorRef, Props}
import com.typesafe.scalalogging.StrictLogging
import press.lis.lise.MessageScheduler._
import press.lis.lise.model.MessageDao.MessageDTO

import scala.collection.JavaConversions._
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.{DurationDouble, FiniteDuration}
import scala.language.postfixOps

/**
  * @author Aleksandr Eliseev
  */
object MessageScheduler {
  type MessageId = Long
  type ScheduledTimeMillis = Long

  def props(router: ActorRef) =
    Props(new MessageScheduler(router))

  case class SnoozedMessage(telegramMessageId: Long, chatId: Long, messageDTO: MessageDTO)

  case class Snooze(message: SnoozedMessage, duration: FiniteDuration)

  case class SendState(sender: (String) => Any)

  case object Tick

}

class MessageScheduler(router: ActorRef) extends Actor with StrictLogging {

  val jobs = new util.TreeMap[ScheduledTimeMillis, SnoozedMessage]()

  val cancellable = context.system.scheduler.schedule(
    5 seconds,
    5 seconds,
    self,
    Tick)

  override def receive: Receive = {
    case Snooze(message, duration) =>

      logger.debug(s"Message [$message] snoozed for [$duration]")

      val scheduledToMillis = System.currentTimeMillis() + duration.toMillis

      jobs.put(scheduledToMillis, message)

    case Tick =>
      val toSendJobs = jobs.headMap(System.currentTimeMillis(), true)

      for (message <- toSendJobs.values()) {
        router ! message
      }

      toSendJobs.clear()

    case SendState(sender) =>
      sender(s"I have ${jobs.size} more jobs.")
  }
}
