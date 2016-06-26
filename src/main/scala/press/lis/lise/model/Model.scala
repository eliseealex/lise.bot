package press.lis.lise.model

import slick.lifted.Tag

import slick.driver.PostgresDriver.api._


/**
  * @author Aleksandr Eliseev
  */
object Model {

  class Users(tag: Tag) extends Table[(Long, String)](tag, "USERS") {
    def id = column[Long]("ID", O.PrimaryKey, O.AutoInc)
    def telegramUserId = column[String]("TELEGRAM_USER_ID")

    def * = (id, telegramUserId)
  }

  val users = TableQuery[Users]

  class Tags(tag: Tag) extends Table[(Long, String)](tag, "TAGS") {
    def id = column[Long]("ID", O.PrimaryKey, O.AutoInc)
    def name = column[String]("NAME")

    def * = (id, name)
  }

  val tags = TableQuery[Tags]

  class Messages(tag: Tag) extends Table[(Long, String, String)](tag, "MESSAGES") {
    def id = column[Long]("ID", O.PrimaryKey, O.AutoInc)
    def telegramMessageId = column[String]("TELEGRAM_MESSAGE_ID")
    def messageText = column[String]("MESSAGE")

    def * = (id, telegramMessageId, messageText)
  }

  val messages = TableQuery[Messages]

  class MessagesUsers(tag: Tag) extends Table[(Long, Long)](tag, "MESSAGES_USERS") {
    def messageId = column[Long]("MESSAGE_ID")
    def userId = column[Long]("USER_ID")

    def * = (messageId, userId)
    def pk = primaryKey("mu_pk", (messageId, userId))
    def mfk = foreignKey("m_fk", messageId, messages)(_.id, onDelete=ForeignKeyAction.Cascade)
    def ufk = foreignKey("u_fk", userId, users)(_.id, onDelete=ForeignKeyAction.Cascade)
  }

  val messagesUsers = TableQuery[MessagesUsers]

  class MessagesTags(tag: Tag) extends Table[(Long, Long)](tag, "MESSAGES_TAGS") {
    def messageId = column[Long]("MESSAGE_ID")
    def tagId = column[Long]("TAGS_ID")

    def * = (messageId, tagId)
    def pk = primaryKey("mt_pk", (messageId, tagId))
    def mfk = foreignKey("m_fk", messageId, messages)(_.id, onDelete=ForeignKeyAction.Cascade)
    def tfk = foreignKey("t_fk", tagId, tags)(_.id, onDelete=ForeignKeyAction.Cascade)
  }

  val messagesTags = TableQuery[Messages]

  val schema = users.schema ++ tags.schema ++ messages.schema ++
    messagesUsers.schema ++ messagesTags.schema
}
