package org.burgas.dao

import io.ktor.http.content.*
import io.ktor.utils.io.*
import kotlinx.io.readByteArray
import org.burgas.database.*
import org.burgas.dto.Dependency
import org.burgas.dto.FileResponse
import org.burgas.dto.Request
import org.burgas.dto.Response
import org.jetbrains.exposed.dao.CompositeEntity
import org.jetbrains.exposed.dao.CompositeEntityClass
import org.jetbrains.exposed.dao.UUIDEntity
import org.jetbrains.exposed.dao.UUIDEntityClass
import org.jetbrains.exposed.dao.id.CompositeID
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.sql.statements.api.ExposedBlob
import java.util.*

interface File

interface Dao

interface Uploader<F : File> {
    suspend fun upload(partData: PartData): F
}

interface EntityMapper<R : Request, D : Dao> {
    suspend fun toEntity(request: R): D
}

interface DependencyMapper<D : Dependency> {
    suspend fun toDependency(): D
}

interface ResponseMapper<F : Response> {
    suspend fun toResponse(): F
}

class FileEntity(id: EntityID<UUID>) : UUIDEntity(id), File, Uploader<FileEntity>, ResponseMapper<FileResponse> {
    companion object : UUIDEntityClass<FileEntity>(FileTable)

    var name by FileTable.name
    var contentType by FileTable.contentType
    var preview by FileTable.preview
    var data by FileTable.data

    @OptIn(InternalAPI::class)
    override suspend fun upload(partData: PartData): FileEntity {
        if (partData is PartData.FileItem) {
            this.name = partData.originalFileName!!
            this.contentType = "${partData.contentType!!.contentType}/${partData.contentType!!.contentSubtype}"
            this.preview = false
            this.data = ExposedBlob(partData.provider().readBuffer.readByteArray())
            return this
        } else {
            throw IllegalArgumentException("Part data not File item")
        }
    }

    override suspend fun toResponse(): FileResponse {
        return FileResponse(
            id = this.id.value,
            name = this.name,
            contentType = this.contentType,
            preview = this.preview
        )
    }
}

class IdentityEntity(id: EntityID<UUID>) : UUIDEntity(id), Dao {
    companion object : UUIDEntityClass<IdentityEntity>(IdentityTable)

    var authority by IdentityTable.authority
    var username by IdentityTable.username
    var password by IdentityTable.password
    var email by IdentityTable.email
    var phone by IdentityTable.phone
    var status by IdentityTable.status
    var firstname by IdentityTable.firstname
    var lastname by IdentityTable.lastname
    var patronymic by IdentityTable.patronymic

    var files by FileEntity via IdentityFileTable
    var contacts by IdentityEntity via IdentityContactTable
    val dialogues by DialogueEntity referrersOn DialogueTable
    var chats by ChatEntity via ChatIdentityTable
    var communities by CommunityEntity via CommunityIdentityTable
}

class DialogueEntity(id: EntityID<CompositeID>) : CompositeEntity(id) {
    companion object : CompositeEntityClass<DialogueEntity>(DialogueTable)

    val messages by DialogueMessageEntity referrersOn DialogueMessageTable

    var created by DialogueTable.created
}

class DialogueMessageEntity(id: EntityID<UUID>) : UUIDEntity(id) {
    companion object : UUIDEntityClass<DialogueMessageEntity>(DialogueMessageTable)

    var dialogue by DialogueEntity referencedOn DialogueMessageTable
    var sender by IdentityEntity optionalReferencedOn DialogueMessageTable.senderId

    var text by DialogueMessageTable.text

    var files by FileEntity via DialogueMessageFileTable

    var created by DialogueMessageTable.created
}

class ChatEntity(id: EntityID<UUID>) : UUIDEntity(id), Dao {
    companion object : UUIDEntityClass<ChatEntity>(ChatTable)

    var name by ChatTable.name
    var description by ChatTable.description
    var created by ChatTable.created
    var opened by ChatTable.opened

    var files by FileEntity via ChatFileTable
    var identities by IdentityEntity via ChatIdentityTable
    val messages by ChatMessageEntity referrersOn ChatMessageTable.chatId
}

class ChatMessageEntity(id: EntityID<UUID>) : UUIDEntity(id) {
    companion object : UUIDEntityClass<ChatMessageEntity>(ChatMessageTable)

    var chat by ChatEntity referencedOn ChatMessageTable.chatId
    var sender by IdentityEntity optionalReferencedOn ChatMessageTable.senderId

    var text by ChatMessageTable.text
    var created by ChatMessageTable.created

    var files by FileEntity via ChatMessageFileTable
}

class CommunityEntity(id: EntityID<UUID>) : UUIDEntity(id), Dao {
    companion object : UUIDEntityClass<CommunityEntity>(CommunityTable)

    var name by CommunityTable.name
    var description by CommunityTable.description
    var opened by CommunityTable.opened
    var created by CommunityTable.created

    var files by FileEntity via CommunityFileTable
    var identities by IdentityEntity via CommunityIdentityTable
    val publications by PublicationEntity referrersOn PublicationTable.communityId
}

class PublicationEntity(id: EntityID<UUID>) : UUIDEntity(id) {
    companion object : UUIDEntityClass<PublicationEntity>(PublicationTable)

    var community by CommunityEntity referencedOn PublicationTable.communityId
    var sender by IdentityEntity optionalReferencedOn PublicationTable.senderId
    val files by FileEntity via PublicationFileTable
    val comments by CommentEntity referrersOn CommentTable.publicationId

    var text by PublicationTable.text
    var created by PublicationTable.created
}

class CommentEntity(id: EntityID<UUID>) : UUIDEntity(id) {
    companion object : UUIDEntityClass<CommentEntity>(CommentTable)

    var publication by PublicationEntity referencedOn CommentTable.publicationId
    var sender by IdentityEntity optionalReferencedOn CommentTable.senderId
    var files by FileEntity via CommentFileTable

    var text by CommentTable.text
    var created by CommentTable.created
}