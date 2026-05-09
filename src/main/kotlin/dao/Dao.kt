package org.burgas.dao

import io.ktor.http.content.*
import io.ktor.utils.io.*
import kotlinx.datetime.toJavaLocalDate
import kotlinx.datetime.toKotlinLocalDate
import kotlinx.datetime.toKotlinLocalDateTime
import kotlinx.io.readByteArray
import org.burgas.database.*
import org.burgas.dto.*
import org.burgas.encryption.RegexType
import org.jetbrains.exposed.dao.CompositeEntity
import org.jetbrains.exposed.dao.CompositeEntityClass
import org.jetbrains.exposed.dao.UUIDEntity
import org.jetbrains.exposed.dao.UUIDEntityClass
import org.jetbrains.exposed.dao.id.CompositeID
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.sql.SizedCollection
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.statements.api.ExposedBlob
import org.mindrot.jbcrypt.BCrypt
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.*

interface File

interface Dao

interface Uploader<F : File> {
    suspend fun upload(partData: PartData): F
}

interface DesignEntity<R : Request> {
    suspend fun insert(request: R)
}

interface ModifyEntity<R : Request> {
    suspend fun update(request: R)
}

interface DependencyMapper<D : Dependency> {
    suspend fun toDependency(): D
}

interface ResponseMapper<F : Response> {
    suspend fun toResponse(): F
}

class FileEntity(id: EntityID<UUID>) : UUIDEntity(id), File, Uploader<FileEntity>, DependencyMapper<FileDependency> {
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

    override suspend fun toDependency(): FileDependency {
        return FileDependency(
            id = this.id.value,
            name = this.name,
            contentType = this.contentType,
            preview = this.preview
        )
    }
}

class IdentityEntity(id: EntityID<UUID>) : UUIDEntity(id), Dao, DesignEntity<IdentityRequest>,
    ModifyEntity<IdentityRequest>, ResponseMapper<IdentityResponse>, DependencyMapper<IdentityDependency> {
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

    override suspend fun insert(request: IdentityRequest) {
        this.authority = request.authority ?: Authority.USER
        this.username = request.username!!
        this.password = BCrypt.hashpw(request.password!!, BCrypt.gensalt())
        this.email = if (RegexType.emailRegex.matches(request.email!!))
            request.email else throw IllegalArgumentException("Email not matched")
        this.phone = if (RegexType.phoneRegex.matches(request.phone!!))
            request.phone else throw IllegalArgumentException("Phone not matched")
        this.status = request.status ?: true
        this.firstname = request.firstname!!
        this.lastname = request.lastname!!
        this.patronymic = request.patronymic!!
    }

    override suspend fun update(request: IdentityRequest) {
        this.authority = request.authority ?: this.authority
        this.username = request.username ?: this.username
        if (request.email != null) {
            this.email = if (RegexType.emailRegex.matches(request.email))
                request.email else throw IllegalArgumentException("Email not matched")
        }
        if (request.phone != null) {
            this.phone = if (RegexType.phoneRegex.matches(request.phone))
                request.phone else throw IllegalArgumentException("Phone not matched")
        }
        this.firstname = request.firstname ?: this.firstname
        this.lastname = request.lastname ?: this.lastname
        this.patronymic = request.patronymic ?: this.patronymic
    }

    suspend fun toIdentityInChat(chatId: UUID): IdentityDependency {
        val chatIdentity = ChatIdentityTable.selectAll()
            .where { (ChatIdentityTable.identityId eq id.value) and (ChatIdentityTable.chatId eq chatId) }
            .single()
        return IdentityDependency(
            id = this.id.value,
            username = this.username,
            email = this.email,
            phone = this.phone,
            firstname = this.firstname,
            lastname = this.lastname,
            patronymic = this.patronymic,
            admin = chatIdentity[ChatIdentityTable.admin],
            files = this.files.map { it.toDependency() }
        )
    }

    suspend fun toIdentityInCommunity(communityId: UUID): IdentityDependency {
        val communityIdentity = CommunityIdentityTable.selectAll()
            .where { (CommunityIdentityTable.communityId eq communityId) and (CommunityIdentityTable.identityId eq id.value) }
            .single()
        return IdentityDependency(
            id = this.id.value,
            username = this.username,
            email = this.email,
            phone = this.phone,
            firstname = this.firstname,
            lastname = this.lastname,
            patronymic = this.patronymic,
            admin = communityIdentity[CommunityIdentityTable.admin],
            files = this.files.map { it.toDependency() }
        )
    }

    override suspend fun toDependency(): IdentityDependency {
        return IdentityDependency(
            id = this.id.value,
            username = this.username,
            email = this.email,
            phone = this.phone,
            firstname = this.firstname,
            lastname = this.lastname,
            patronymic = this.patronymic,
            files = this.files.map { it.toDependency() }
        )
    }

    override suspend fun toResponse(): IdentityResponse {
        return IdentityResponse(
            id = this.id.value,
            username = this.username,
            email = this.email,
            phone = this.phone,
            firstname = this.firstname,
            lastname = this.lastname,
            patronymic = this.patronymic,
            files = this.files.map { it.toDependency() },
            contacts = this.contacts.map { it.toDependency() }
        )
    }
}

class DialogueEntity(id: EntityID<UUID>) : UUIDEntity(id) {
    companion object : UUIDEntityClass<DialogueEntity>(DialogueTable)

    var identities by IdentityEntity via DialogueIdentityTable
    val messages by DialogueMessageEntity referrersOn DialogueMessageTable

    var created by DialogueTable.created
}

class DialogueMessageEntity(id: EntityID<UUID>) : UUIDEntity(id), Dao, DesignEntity<DialogueMessageRequest> {
    companion object : UUIDEntityClass<DialogueMessageEntity>(DialogueMessageTable)

    var dialogue by DialogueEntity referencedOn DialogueMessageTable.dialogueId
    var sender by IdentityEntity optionalReferencedOn DialogueMessageTable.senderId

    var text by DialogueMessageTable.text

    var files by FileEntity via DialogueMessageFileTable

    var created by DialogueMessageTable.created

    override suspend fun insert(request: DialogueMessageRequest) {
        val dialogueEntity = DialogueEntity.findById(request.dialogueId ?: UUID(0,0)) ?: DialogueEntity.new {
            if (request.firstCompanionId!! == request.secondCompanionId!!) throw IllegalArgumentException("Wrong dialogue identities")
            this.created = LocalDate.now().toKotlinLocalDate()
            val firstCompanion = IdentityEntity.findById(request.firstCompanionId)!!
            val secondCompanion = IdentityEntity.findById(request.secondCompanionId)!!
            this.identities = SizedCollection(firstCompanion, secondCompanion)
        }
        this.dialogue = dialogueEntity
        val sender = IdentityEntity.findById(request.senderId!!)!!
        if (dialogueEntity.identities.contains(sender)) {
            this.sender = sender
        } else {
            throw IllegalArgumentException("This sender not in this dialogue")
        }
        this.text = request.text!!
        this.created = LocalDateTime.now().toKotlinLocalDateTime()
    }
}

class ChatEntity(id: EntityID<UUID>) : UUIDEntity(id), Dao, DesignEntity<ChatRequest>, ModifyEntity<ChatRequest> {
    companion object : UUIDEntityClass<ChatEntity>(ChatTable)

    var name by ChatTable.name
    var description by ChatTable.description
    var created by ChatTable.created
    var opened by ChatTable.opened

    var files by FileEntity via ChatFileTable
    var identities by IdentityEntity via ChatIdentityTable
    val messages by ChatMessageEntity referrersOn ChatMessageTable.chatId

    override suspend fun insert(request: ChatRequest) {
        this.name = request.name!!
        this.description = request.description!!
        this.opened = request.opened!!
        this.created = LocalDate.now().toKotlinLocalDate()
    }

    override suspend fun update(request: ChatRequest) {
        this.name = request.name ?: this.name
        this.description = request.description ?: this.description
        this.opened = request.opened ?: this.opened
        if (request.adminId != null) {
            val newAdmin = ChatIdentityTable.selectAll()
                .where { (ChatIdentityTable.chatId eq request.id!!) and (ChatIdentityTable.identityId eq request.adminId) }
                .single()
            newAdmin[ChatIdentityTable.admin] = true
        }
    }
}

class ChatMessageEntity(id: EntityID<UUID>) : UUIDEntity(id), Dao, DesignEntity<ChatMessageRequest> {
    companion object : UUIDEntityClass<ChatMessageEntity>(ChatMessageTable)

    var chat by ChatEntity referencedOn ChatMessageTable.chatId
    var sender by IdentityEntity optionalReferencedOn ChatMessageTable.senderId

    var text by ChatMessageTable.text
    var created by ChatMessageTable.created

    var files by FileEntity via ChatMessageFileTable

    override suspend fun insert(request: ChatMessageRequest) {
        this.chat = ChatEntity.findById(request.chatId!!)!!
        this.sender = IdentityEntity.findById(request.senderId!!)!!
        this.text = request.text!!
        this.created = LocalDateTime.now().toKotlinLocalDateTime()
    }
}

class CommunityEntity(id: EntityID<UUID>) : UUIDEntity(id), Dao, DesignEntity<CommunityRequest>, ModifyEntity<CommunityRequest> {
    companion object : UUIDEntityClass<CommunityEntity>(CommunityTable)

    var name by CommunityTable.name
    var description by CommunityTable.description
    var opened by CommunityTable.opened
    var created by CommunityTable.created

    var files by FileEntity via CommunityFileTable
    var identities by IdentityEntity via CommunityIdentityTable
    val publications by PublicationEntity referrersOn PublicationTable.communityId

    override suspend fun insert(request: CommunityRequest) {
        this.name = request.name!!
        this.description = request.description!!
        this.opened = request.opened!!
        this.created = LocalDate.now().toKotlinLocalDate()
    }

    override suspend fun update(request: CommunityRequest) {
        this.name = request.name ?: this.name
        this.description = request.description ?: this.description
        this.opened = request.opened ?: this.opened
        if (request.adminId != null) {
            val newAdmin = CommunityIdentityTable.selectAll()
                .where { (CommunityIdentityTable.communityId eq request.id!!) and (CommunityIdentityTable.identityId eq request.adminId) }
                .single()
            newAdmin[CommunityIdentityTable.admin] = true
        }
    }
}

class PublicationEntity(id: EntityID<UUID>) : UUIDEntity(id), Dao, DesignEntity<PublicationRequest> {
    companion object : UUIDEntityClass<PublicationEntity>(PublicationTable)

    var community by CommunityEntity referencedOn PublicationTable.communityId
    var sender by IdentityEntity optionalReferencedOn PublicationTable.senderId
    val files by FileEntity via PublicationFileTable
    val comments by CommentEntity referrersOn CommentTable.publicationId

    var text by PublicationTable.text
    var created by PublicationTable.created

    override suspend fun insert(request: PublicationRequest) {
        this.community = CommunityEntity.findById(request.communityId!!)!!
        this.sender = IdentityEntity.findById(request.senderId!!)!!
        this.text = request.text!!
        this.created = LocalDateTime.now().toKotlinLocalDateTime()
    }
}

class CommentEntity(id: EntityID<UUID>) : UUIDEntity(id), Dao, DesignEntity<CommentRequest> {
    companion object : UUIDEntityClass<CommentEntity>(CommentTable)

    var publication by PublicationEntity referencedOn CommentTable.publicationId
    var sender by IdentityEntity optionalReferencedOn CommentTable.senderId
    var files by FileEntity via CommentFileTable

    var text by CommentTable.text
    var created by CommentTable.created

    override suspend fun insert(request: CommentRequest) {
        this.publication = PublicationEntity.findById(request.publicationId!!)!!
        this.sender = IdentityEntity.findById(request.senderId!!)!!
        this.text = request.text!!
        this.created = LocalDateTime.now().toKotlinLocalDateTime()
    }
}