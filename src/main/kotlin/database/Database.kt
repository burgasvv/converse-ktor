
@file:Suppress("UnusedReceiverParameter")

package org.burgas.database

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import io.ktor.server.application.*
import io.ktor.server.config.*
import org.jetbrains.exposed.dao.id.CompositeIdTable
import org.jetbrains.exposed.dao.id.UUIDTable
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.ReferenceOption
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.kotlin.datetime.CurrentDate
import org.jetbrains.exposed.sql.kotlin.datetime.CurrentDateTime
import org.jetbrains.exposed.sql.kotlin.datetime.date
import org.jetbrains.exposed.sql.kotlin.datetime.datetime
import org.jetbrains.exposed.sql.transactions.transaction
import redis.clients.jedis.Jedis

object DatabaseConnection {

    private val config = ApplicationConfig("application.yaml")

    private val hikariConfig = HikariConfig().apply {
        driverClassName = config.property("ktor.postgres.driver").getString()
        jdbcUrl = config.property("ktor.postgres.url").getString()
        username = config.property("ktor.postgres.username").getString()
        password = config.property("ktor.postgres.password").getString()
        maximumPoolSize = 20
        minimumIdle = 5
        isAutoCommit = false
        validate()
    }

    private val dataSource = HikariDataSource(hikariConfig)

    val postgres = Database.connect(datasource = dataSource)

    val redis = Jedis(
        config.property("ktor.redis.host").getString(),
        config.property("ktor.redis.port").getString().toInt()
    )
}

object FileTable : UUIDTable("file") {
    val name = varchar("file", 250)
    val contentType = varchar("content_type", 250)
    val preview = bool("preview").default(false)
    val data = blob("data")
}

enum class Authority {
    ADMIN, USER
}

object IdentityTable : UUIDTable("identity") {
    val authority = enumerationByName<Authority>("authority", 250)
    val username = varchar("username", 250).uniqueIndex()
    val password = varchar("password", 50)
    val email = varchar("email", 250).uniqueIndex()
    val phone = varchar("phone", 25).uniqueIndex()
    val status = bool("status").default(true)
    val firstname = varchar("firstname", 250)
    val lastname = varchar("lastname", 250)
    val patronymic = varchar("patronymic", 250)
}

object IdentityFileTable : Table("identity_file") {
    val identityId = reference(
        name = "identity_id", refColumn = IdentityTable.id,
        onDelete = ReferenceOption.CASCADE, onUpdate = ReferenceOption.CASCADE
    )
    val fileId = reference(
        name = "file_id", refColumn = FileTable.id,
        onDelete = ReferenceOption.CASCADE, onUpdate = ReferenceOption.CASCADE
    )
    override val primaryKey: PrimaryKey
        get() = PrimaryKey(arrayOf(identityId, fileId))
}

object IdentityContactTable : Table("identity_contact") {
    val identityId = reference(
        name = "identity_id", refColumn = IdentityTable.id,
        onDelete = ReferenceOption.CASCADE, onUpdate = ReferenceOption.CASCADE
    )
    val contactId = reference(
        name = "contact_id", refColumn = IdentityTable.id,
        onDelete = ReferenceOption.CASCADE, onUpdate = ReferenceOption.CASCADE
    )
    override val primaryKey: PrimaryKey
        get() = PrimaryKey(arrayOf(identityId, contactId))
}

object DialogueTable : CompositeIdTable("dialogue") {
    val firstCompanionId = reference(
        name = "first_companion_id", refColumn = IdentityTable.id,
        onDelete = ReferenceOption.CASCADE, onUpdate = ReferenceOption.CASCADE
    )
    val secondCompanionId = reference(
        name = "second_companion_id", refColumn = IdentityTable.id,
        onDelete = ReferenceOption.CASCADE, onUpdate = ReferenceOption.CASCADE
    )
    val created = date("created").defaultExpression(CurrentDate)
    override val primaryKey: PrimaryKey
        get() = PrimaryKey(arrayOf(firstCompanionId, secondCompanionId))
}

object DialogueMessageTable : UUIDTable("dialogue_message") {
    val dialogueFirstCompanionId = uuid("dialogue_first_companion_id")
    val dialogueSecondCompanionId = uuid("dialogue_second_companion_id")
    val senderId = optReference(
        name = "sender_id", refColumn = IdentityTable.id,
        onDelete = ReferenceOption.CASCADE, onUpdate = ReferenceOption.CASCADE
    )
    val text = text("text")
    val created = datetime("created").defaultExpression(CurrentDateTime)
    init {
        foreignKey(dialogueFirstCompanionId, dialogueSecondCompanionId, target = DialogueTable.primaryKey)
    }
}

object ChatTable : UUIDTable("chat") {
    val name = varchar("name", 250).uniqueIndex()
    val description = text("description")
    val created = date("created").defaultExpression(CurrentDate)
    val opened = bool("opened").default(true)
}

object ChatFileTable : Table("chat_file") {
    val chatId = reference(
        name = "chat_id", refColumn = ChatTable.id,
        onDelete = ReferenceOption.CASCADE, onUpdate = ReferenceOption.CASCADE
    )
    val fileId = reference(
        name = "file_id", refColumn = FileTable.id,
        onDelete = ReferenceOption.CASCADE, onUpdate = ReferenceOption.CASCADE
    )
    override val primaryKey: PrimaryKey
        get() = PrimaryKey(arrayOf(chatId, fileId))
}

object ChatIdentityTable : Table("chat_identity") {
    val chatId = reference(
        name = "chat_id", refColumn = ChatTable.id,
        onDelete = ReferenceOption.CASCADE, onUpdate = ReferenceOption.CASCADE
    )
    val identityId = reference(
        name = "identity_id", refColumn = IdentityTable.id,
        onDelete = ReferenceOption.CASCADE, onUpdate = ReferenceOption.CASCADE
    )
    val admin = bool("admin").default(false)
    override val primaryKey: PrimaryKey
        get() = PrimaryKey(arrayOf(chatId, identityId))
}

object ChatMessageTable : UUIDTable("chat_message") {
    val chatId = reference(
        name = "chat_id", refColumn = ChatTable.id,
        onDelete = ReferenceOption.CASCADE, onUpdate = ReferenceOption.CASCADE
    )
    val senderId = optReference(
        name = "sender_id", refColumn = IdentityTable.id,
        onDelete = ReferenceOption.SET_NULL, onUpdate = ReferenceOption.CASCADE
    )
    val text = text("text")
    val created = datetime("created").defaultExpression(CurrentDateTime)
}

object ChatMessageFileTable : Table("chat_message_file") {
    val chatMessageId = reference(
        name = "chat_id", refColumn = ChatMessageTable.id,
        onDelete = ReferenceOption.CASCADE, onUpdate = ReferenceOption.CASCADE
    )
    val fileId = reference(
        name = "file_id", refColumn = FileTable.id,
        onDelete = ReferenceOption.CASCADE, onUpdate = ReferenceOption.CASCADE
    )
    override val primaryKey: PrimaryKey
        get() = PrimaryKey(arrayOf(chatMessageId, fileId))
}

object CommunityTable : UUIDTable("community") {
    val name = varchar("name", 250).uniqueIndex()
    val description = text("description")
    val created = date("created").defaultExpression(CurrentDate)
    val opened = bool("opened")
}

object CommunityFileTable : Table("community_file") {
    val communityId = reference(
        name = "community_id", refColumn = CommunityTable.id,
        onDelete = ReferenceOption.CASCADE, onUpdate = ReferenceOption.CASCADE
    )
    val fileId = reference(
        name = "file_id", refColumn = FileTable.id,
        onDelete = ReferenceOption.CASCADE, onUpdate = ReferenceOption.CASCADE
    )
    override val primaryKey: PrimaryKey
        get() = PrimaryKey(arrayOf(communityId, fileId))
}

object CommunityIdentityTable : Table("community_identity") {
    val communityId = reference(
        name = "community_id", refColumn = CommunityTable.id,
        onDelete = ReferenceOption.CASCADE, onUpdate = ReferenceOption.CASCADE
    )
    val identityId = reference(
        name = "identity_id", refColumn = IdentityTable.id,
        onDelete = ReferenceOption.CASCADE, onUpdate = ReferenceOption.CASCADE
    )
    override val primaryKey: PrimaryKey
        get() = PrimaryKey(arrayOf(communityId, identityId))
}

object PublicationTable : UUIDTable("publication") {
    val communityId = reference(
        name = "community_id", refColumn = CommunityTable.id,
        onDelete = ReferenceOption.CASCADE, onUpdate = ReferenceOption.CASCADE
    )
    val senderId = optReference(
        name = "sender_id", refColumn = IdentityTable.id,
        onDelete = ReferenceOption.SET_NULL, onUpdate = ReferenceOption.CASCADE
    )
    val text = text("text")
    val created = datetime("created").defaultExpression(CurrentDateTime)
}

object PublicationFileTable : Table("publication_file") {
    val publicationId = reference(
        name = "publication_id", refColumn = PublicationTable.id,
        onDelete = ReferenceOption.CASCADE, onUpdate = ReferenceOption.CASCADE
    )
    val fileId = reference(
        name = "file_id", refColumn = FileTable.id,
        onDelete = ReferenceOption.CASCADE, onUpdate = ReferenceOption.CASCADE
    )
    override val primaryKey: PrimaryKey
        get() = PrimaryKey(arrayOf(publicationId, fileId))
}

object CommentTable : UUIDTable("comment") {
    val publicationId = reference(
        name = "publication_id", refColumn = PublicationTable.id,
        onDelete = ReferenceOption.CASCADE, onUpdate = ReferenceOption.CASCADE
    )
    val senderId = optReference(
        name = "sender_id", refColumn = IdentityTable.id,
        onDelete = ReferenceOption.SET_NULL, onUpdate = ReferenceOption.CASCADE
    )
    val text = text("text")
    val created = datetime("created").defaultExpression(CurrentDateTime)
}

object CommentFileTable : Table("comment_file") {
    val commentId = reference(
        name = "comment_id", refColumn = CommentTable.id,
        onDelete = ReferenceOption.CASCADE, onUpdate = ReferenceOption.CASCADE
    )
    val fileId = reference(
        name = "file_id", refColumn = FileTable.id,
        onDelete = ReferenceOption.CASCADE, onUpdate = ReferenceOption.CASCADE
    )
    override val primaryKey: PrimaryKey
        get() = PrimaryKey(arrayOf(commentId, fileId))
}

fun Application.configureDatabase() {
    transaction(db = DatabaseConnection.postgres) {
        SchemaUtils.create(
            FileTable, IdentityTable, IdentityFileTable, IdentityContactTable, DialogueTable,
            DialogueMessageTable, ChatTable, ChatFileTable, ChatIdentityTable, ChatMessageTable,
            ChatMessageFileTable, CommunityTable, CommunityFileTable, CommunityIdentityTable, PublicationTable,
            PublicationFileTable, CommentTable, CommentFileTable
        )
    }
}