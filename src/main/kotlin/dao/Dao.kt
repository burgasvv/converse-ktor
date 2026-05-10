package org.burgas.dao

import io.ktor.http.content.*
import io.ktor.utils.io.*
import kotlinx.datetime.toJavaLocalDate
import kotlinx.datetime.toJavaLocalDateTime
import kotlinx.datetime.toKotlinLocalDate
import kotlinx.datetime.toKotlinLocalDateTime
import kotlinx.io.readByteArray
import org.burgas.database.*
import org.burgas.dto.*
import org.burgas.encryption.RegexType
import org.jetbrains.exposed.dao.UUIDEntity
import org.jetbrains.exposed.dao.UUIDEntityClass
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
    val dialogues by DialogueEntity via DialogueIdentityTable
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
            contacts = this.contacts.map { it.toDependency() },
            dialogues = this.dialogues.map { it.toDependency() },
            chats = this.chats.map { it.toDependency() },
            communities = this.communities.map { it.toDependency() }
        )
    }
}

class DialogueEntity(id: EntityID<UUID>) : UUIDEntity(id), Dao, DependencyMapper<DialogueDependency>, ResponseMapper<DialogueResponse> {
    companion object : UUIDEntityClass<DialogueEntity>(DialogueTable)

    var identities by IdentityEntity via DialogueIdentityTable
    val messages by DialogueMessageEntity referrersOn DialogueMessageTable

    var created by DialogueTable.created

    override suspend fun toDependency(): DialogueDependency {
        return DialogueDependency(
            id = this.id.value,
            identities = this.identities.map { it.toDependency() },
            created = this.created.toJavaLocalDate().format(DateTimeFormatter.ofPattern("dd MMMM yyyy"))
        )
    }

    override suspend fun toResponse(): DialogueResponse {
        return DialogueResponse(
            id = this.id.value,
            identities = this.identities.map { it.toDependency() },
            messages = this.messages.map { it.toDependency() },
            created = this.created.toJavaLocalDate().format(DateTimeFormatter.ofPattern("dd MMMM yyyy"))
        )
    }
}

class DialogueMessageEntity(id: EntityID<UUID>) : UUIDEntity(id), Dao, DesignEntity<DialogueMessageRequest>,
    DependencyMapper<DialogueMessageDependency>, ResponseMapper<DialogueMessageResponse> {
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

    override suspend fun toDependency(): DialogueMessageDependency {
        return DialogueMessageDependency(
            id = this.id.value,
            sender = this.sender?.toDependency(),
            text = this.text,
            files = this.files.map { it.toDependency() },
            created = this.created.toJavaLocalDateTime().format(DateTimeFormatter.ofPattern("dd MMMM yyyy, hh:mm"))
        )
    }

    override suspend fun toResponse(): DialogueMessageResponse {
        return DialogueMessageResponse(
            id = this.id.value,
            dialogue = this.dialogue.toDependency(),
            sender = this.sender?.toDependency(),
            text = this.text,
            files = this.files.map { it.toDependency() },
            created = this.created.toJavaLocalDateTime().format(DateTimeFormatter.ofPattern("dd MMMM yyyy, hh:mm"))
        )
    }
}

class ChatEntity(id: EntityID<UUID>) : UUIDEntity(id), Dao, DesignEntity<ChatRequest>, ModifyEntity<ChatRequest>,
    DependencyMapper<ChatDependency>, ResponseMapper<ChatResponse> {
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

    override suspend fun toDependency(): ChatDependency {
        return ChatDependency(
            id = this.id.value,
            name = this.name,
            description = this.description,
            opened = this.opened,
            created = this.created.toJavaLocalDate().format(DateTimeFormatter.ofPattern("dd MMMM yyyy")),
            files = this.files.map { it.toDependency() }
        )
    }

    override suspend fun toResponse(): ChatResponse {
        return ChatResponse(
            id = this.id.value,
            name = this.name,
            description = this.description,
            opened = this.opened,
            created = this.created.toJavaLocalDate().format(DateTimeFormatter.ofPattern("dd MMMM yyyy")),
            identities = this.identities.map { it.toIdentityInChat(this.id.value) },
            messages = this.messages.map { it.toDependency() },
            files = this.files.map { it.toDependency() }
        )
    }
}

class ChatMessageEntity(id: EntityID<UUID>) : UUIDEntity(id), Dao, DesignEntity<ChatMessageRequest>,
    DependencyMapper<ChatMessageDependency>, ResponseMapper<ChatMessageResponse> {
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

    override suspend fun toDependency(): ChatMessageDependency {
        return ChatMessageDependency(
            id = this.id.value,
            sender = this.sender?.toDependency(),
            text = this.text,
            created = this.created.toJavaLocalDateTime().format(DateTimeFormatter.ofPattern("dd MMMM yyyy, hh:mm")),
            files = this.files.map { it.toDependency() }
        )
    }

    override suspend fun toResponse(): ChatMessageResponse {
        return ChatMessageResponse(
            id = this.id.value,
            chat = this.chat.toDependency(),
            sender = this.sender?.toDependency(),
            text = this.text,
            created = this.created.toJavaLocalDateTime().format(DateTimeFormatter.ofPattern("dd MMMM yyyy, hh:mm")),
            files = this.files.map { it.toDependency() },
        )
    }
}

class CommunityEntity(id: EntityID<UUID>) : UUIDEntity(id), Dao, DesignEntity<CommunityRequest>, ModifyEntity<CommunityRequest>,
    DependencyMapper<CommunityDependency>, ResponseMapper<CommunityResponse> {
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

    override suspend fun toDependency(): CommunityDependency {
        return CommunityDependency(
            id = this.id.value,
            name = this.name,
            description = this.description,
            opened = this.opened,
            created = this.created.toJavaLocalDate().format(DateTimeFormatter.ofPattern("dd MMMM yyyy")),
            files = this.files.map { it.toDependency() }
        )
    }

    override suspend fun toResponse(): CommunityResponse {
        return CommunityResponse(
            id = this.id.value,
            name = this.name,
            description = this.description,
            opened = this.opened,
            created = this.created.toJavaLocalDate().format(DateTimeFormatter.ofPattern("dd MMMM yyyy")),
            files = this.files.map { it.toDependency() },
            identities = this.identities.map { it.toIdentityInCommunity(this.id.value) },
            publications = this.publications.map { it.toDependency() }
        )
    }
}

class PublicationEntity(id: EntityID<UUID>) : UUIDEntity(id), Dao, DesignEntity<PublicationRequest>,
    DependencyMapper<PublicationDependency>, ResponseMapper<PublicationResponse> {
    companion object : UUIDEntityClass<PublicationEntity>(PublicationTable)

    var community by CommunityEntity referencedOn PublicationTable.communityId
    var sender by IdentityEntity optionalReferencedOn PublicationTable.senderId
    val files by FileEntity via PublicationFileTable
    val comments by CommentEntity referrersOn CommentTable.publicationId

    var text by PublicationTable.text
    var created by PublicationTable.created

    override suspend fun insert(request: PublicationRequest) {
        val communityIdentity = CommunityIdentityTable.selectAll()
            .where {
                (CommunityIdentityTable.communityId eq request.communityId!!) and
                        (CommunityIdentityTable.identityId eq request.senderId!!)
            }.single()
        if (communityIdentity[CommunityIdentityTable.admin]) {
            this.community = CommunityEntity.findById(request.communityId!!)!!
            this.sender = IdentityEntity.findById(request.senderId!!)!!
            this.text = request.text!!
            this.created = LocalDateTime.now().toKotlinLocalDateTime()
        } else {
            throw IllegalArgumentException("Sender must be admin status for sending publication")
        }
    }

    override suspend fun toDependency(): PublicationDependency {
        return PublicationDependency(
            id = this.id.value,
            sender = this.sender?.toDependency(),
            text = this.text,
            created = this.created.toJavaLocalDateTime().format(DateTimeFormatter.ofPattern("dd MMMM yyyy, hh:mm")),
            files = this.files.map { it.toDependency() }
        )
    }

    override suspend fun toResponse(): PublicationResponse {
        return PublicationResponse(
            id = this.id.value,
            community = this.community.toDependency(),
            sender = this.sender?.toDependency(),
            text = this.text,
            created = this.created.toJavaLocalDateTime().format(DateTimeFormatter.ofPattern("dd MMMM yyyy, hh:mm")),
            files = this.files.map { it.toDependency() },
            comments = this.comments.map { it.toDependency() }
        )
    }
}

class CommentEntity(id: EntityID<UUID>) : UUIDEntity(id), Dao, DesignEntity<CommentRequest>,
    DependencyMapper<CommentDependency>, ResponseMapper<CommentResponse> {
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

    override suspend fun toDependency(): CommentDependency {
        return CommentDependency(
            id = this.id.value,
            sender = this.sender?.toDependency(),
            text = this.text,
            created = this.created.toJavaLocalDateTime().format(DateTimeFormatter.ofPattern("dd MMMM yyyy, hh:mm")),
            files = this.files.map { it.toDependency() }
        )
    }

    override suspend fun toResponse(): CommentResponse {
        return CommentResponse(
            id = this.id.value,
            publication = this.publication.toDependency(),
            sender = this.sender?.toDependency(),
            text = this.text,
            created = this.created.toJavaLocalDateTime().format(DateTimeFormatter.ofPattern("dd MMMM yyyy, hh:mm")),
            files = this.files.map { it.toDependency() }
        )
    }
}