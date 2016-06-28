package press.lis.lise.model

import slick.driver.PostgresDriver.api._


/**
  * @author Aleksandr Eliseev
  */
object Model {

  case class Source(id: Option[Long], telegramChatId: String)

  class Sources(tag: Tag) extends Table[Source](tag, "SOURCES") {
    def id = column[Option[Long]]("ID", O.PrimaryKey, O.AutoInc)
    def telegramChatId = column[String]("TELEGRAM_CHAT_ID")

    def * = (id, telegramChatId) <> (Source.tupled, Source.unapply)

    def tciIdx = index("tci_idx", telegramChatId, unique = true)
  }
  val sources = TableQuery[Sources]

  case class Hashtag(id: Option[Long], name: String)

  class Tags(tag: Tag) extends Table[Hashtag](tag, "TAGS") {
    def id = column[Option[Long]]("ID", O.PrimaryKey, O.AutoInc)
    def name = column[String]("NAME")

    def * = (id, name) <> (Hashtag.tupled, Hashtag.unapply)
  }
  val tags = TableQuery[Tags]

  case class Message(id: Option[Long], telegramMessageId: String, messageText: String)

  class Messages(tag: Tag) extends Table[Message](tag, "MESSAGES") {
    def id = column[Option[Long]]("ID", O.AutoInc, O.PrimaryKey)
    def telegramMessageId = column[String]("TELEGRAM_MESSAGE_ID")
    def messageText = column[String]("MESSAGE")

    def * = (id, telegramMessageId, messageText) <> (Message.tupled, Message.unapply)
  }

  val messages = TableQuery[Messages]

  case class MessageToSource(messageId: Option[Long], sourceId: Option[Long])

  class MessagesSources(tag: Tag) extends Table[MessageToSource](tag, "MESSAGES_SOURCES") {
    def messageId = column[Option[Long]]("MESSAGE_ID")
    def sourceId = column[Option[Long]]("SOURCE_ID")

    def * = (messageId, sourceId) <> (MessageToSource.tupled, MessageToSource.unapply)

    def pk = primaryKey("mu_pk", (messageId, sourceId))
    def mfk = foreignKey("m_fk", messageId, messages)(_.id, onDelete=ForeignKeyAction.Cascade)
    def ufk = foreignKey("s_fk", sourceId, sources)(_.id, onDelete=ForeignKeyAction.Cascade)
  }

  val messagesSources = TableQuery[MessagesSources]

  case class MessageToTag(messageId: Option[Long], tagId: Option[Long])

  class MessagesTags(tag: Tag) extends Table[MessageToTag](tag, "MESSAGES_TAGS") {
    def messageId = column[Option[Long]]("MESSAGE_ID")
    def tagId = column[Option[Long]]("TAGS_ID")

    def * = (messageId, tagId) <> (MessageToTag.tupled, MessageToTag.unapply)

    def pk = primaryKey("mt_pk", (messageId, tagId))
    def mfk = foreignKey("m_fk", messageId, messages)(_.id, onDelete=ForeignKeyAction.Cascade)
    def tfk = foreignKey("t_fk", tagId, tags)(_.id, onDelete=ForeignKeyAction.Cascade)
  }

  val messagesTags = TableQuery[Messages]

  val schema = sources.schema ++ tags.schema ++ messages.schema ++
    messagesSources.schema ++ messagesTags.schema
}
