package press.lis.lise.bot

import java.util.Comparator

import akka.actor.{Actor, ActorRef, Props}
import akka.persistence.{PersistentActor, SnapshotOffer}
import com.google.common.collect.TreeMultimap
import com.typesafe.scalalogging.StrictLogging
import press.lis.lise.bot.MessageScheduler._
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

  sealed trait SchedulingEvent

  case class SnoozedMessage(telegramMessageId: Long, chatId: Long, messageDTO: MessageDTO)

  case class Snooze(message: SnoozedMessage, duration: FiniteDuration)

  case class SendState(sender: (String) => Any)

  case class Add(scheduledTimeMillis: ScheduledTimeMillis, snoozedMessage: SnoozedMessage) extends SchedulingEvent

  case class Process(scheduledTimeMillis: ScheduledTimeMillis, snoozedMessage: SnoozedMessage) extends SchedulingEvent

  case class State(jobs: TreeMultimap[ScheduledTimeMillis, SnoozedMessage])

  case object ProcessScheduling

  case object ProcessSnapshot

  object LongComparator extends Comparator[ScheduledTimeMillis] with Serializable {
    override def compare(o1: ScheduledTimeMillis, o2: ScheduledTimeMillis): Int = o1.compareTo(o2)
  }

  object SnoozedMessageComparator extends Comparator[SnoozedMessage] with Serializable {
    override def compare(o1: SnoozedMessage, o2: SnoozedMessage): Int =
      o1.telegramMessageId.compareTo(o2.telegramMessageId)
  }

}

class MessageScheduler(router: ActorRef) extends Actor with PersistentActor with StrictLogging {

  val cancelSchedulingProcessing = context.system.scheduler.schedule(
    5 seconds,
    5 seconds,
    self,
    ProcessScheduling)
  val cancelSnapshotProcessing = context.system.scheduler.schedule(
    60 minutes,
    60 minutes,
    self,
    ProcessSnapshot)
  var jobs: TreeMultimap[ScheduledTimeMillis, SnoozedMessage] =
    TreeMultimap.create[ScheduledTimeMillis, SnoozedMessage](LongComparator, SnoozedMessageComparator)

  override def persistenceId: String = "MessageScheduler-1"

  override def receiveRecover: Receive = {
    case schedulingEvent: SchedulingEvent =>
      updateState(schedulingEvent)
    case SnapshotOffer(metadata, snapshot: State) =>
      jobs = snapshot.jobs

      deleteMessages(metadata.sequenceNr)
  }

  override def receiveCommand: Receive = {
    case Snooze(message, duration) =>

      logger.debug(s"Message [$message] snoozed for [$duration]")

      val scheduledToMillis = System.currentTimeMillis() + duration.toMillis

      persist(Add(scheduledToMillis, message))(add => updateState(add))

    case ProcessScheduling =>
      val toSendJobs = jobs.asMap().headMap(System.currentTimeMillis(), true)

      for (
        scheduledEntry <- toSendJobs.entrySet();
        message <- scheduledEntry.getValue
      ) {
        persist(Process(scheduledEntry.getKey, message))(process => {
          router ! process.snoozedMessage
          updateState(process)
        })
      }

      toSendJobs.clear()

    case SendState(sender) =>
      sender(s"I have ${jobs.size} more jobs.")

    case ProcessSnapshot =>
      saveSnapshot(State(jobs))
  }

  def updateState(schedulingEvent: SchedulingEvent): Unit = schedulingEvent match {
    case Add(scheduledTimeMillis, snoozedMessage) =>
      jobs.put(scheduledTimeMillis, snoozedMessage)
    case Process(scheduledTimeMillis, snoozedMessage) =>
      jobs.remove(scheduledTimeMillis, snoozedMessage)
    case unknownEvent =>
      logger.error(s"Unknown event $unknownEvent")
  }
}
