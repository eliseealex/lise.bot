package press.lis.lise

import java.util

import com.twitter.Extractor

import scala.collection.JavaConversions._

/**
  * @author Aleksandr Eliseev
  */
object MessageParser {

  val extractor = new Extractor

  def parse(message: Option[String]): BotMessage = {

    message match {
      case Some(command) if !command.contains(" ") && command.length > 0 &&
        command.charAt(0).equals('/') =>

        Command(command.substring(1))

      case Some(hashTag) if !hashTag.contains(" ") && hashTag.length > 0 &&
        (hashTag.charAt(0).equals('#') || hashTag.charAt(0).equals('＃')) =>

        HashTag(hashTag.substring(1))

      case Some(text) =>

        val hashtags: util.List[String] = extractor.extractHashtags(text)

        TextMessage(text, hashtags)

      case _ =>

        Unknown
    }
  }

  sealed trait BotMessage

  case class HashTag(tag: String) extends BotMessage

  case class TextMessage(text: String, hashTags: Seq[String]) extends BotMessage

  case class Command(command: String) extends BotMessage

  case object Unknown extends BotMessage

}
