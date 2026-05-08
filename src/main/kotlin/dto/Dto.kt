package org.burgas.dto

import kotlinx.serialization.Serializable
import org.burgas.database.Authority
import org.burgas.serialization.UUIDSerializer
import java.util.*

interface Request {
    val id: UUID?
}

interface Dependency {
    val id: UUID?
}

interface Response {
    val id: UUID?
}

@Serializable
data class CsrfToken(@Serializable(with = UUIDSerializer::class) val value: UUID)

@Serializable
data class ExceptionResponse(
    val status: String,
    val code: Int,
    val message: String
)

@Serializable
data class FileResponse(
    @Serializable(with = UUIDSerializer::class)
    override val id: UUID?,
    val name: String?,
    val contentType: String?,
    val preview: Boolean?
) : Response

@Serializable
data class IdentityRequest(
    @Serializable(with = UUIDSerializer::class)
    override val id: UUID?,
    val authority: Authority?,
    val username: String?,
    val password: String?,
    val email: String?,
    val phone: String?,
    val status: Boolean?,
    val firstname: String?,
    val lastname: String?,
    val patronymic: String?
): Request

@Serializable
data class IdentityDependency(
    @Serializable(with = UUIDSerializer::class)
    override val id: UUID?,
    val username: String?,
    val email: String?,
    val phone: String?,
    val firstname: String?,
    val lastname: String?,
    val patronymic: String?,
    val files: List<FileResponse>?
): Dependency

@Serializable
data class IdentityResponse(
    @Serializable(with = UUIDSerializer::class)
    override val id: UUID?,
    val username: String?,
    val email: String?,
    val phone: String?,
    val firstname: String?,
    val lastname: String?,
    val patronymic: String?,
    val files: List<FileResponse>?,
    val contacts: List<IdentityDependency>?,
    val dialogues: List<DialogueResponse>?,
    val chats: List<ChatDependency>?,
    val communities: List<CommunityDependency>?
): Response

@Serializable
data class DialogueResponse(
    @Serializable(with = UUIDSerializer::class)
    override val id: UUID?,
    val firstCompanion: IdentityDependency?,
    val secondCompanion: IdentityDependency?,
    val created: String?
) : Response

@Serializable
data class DialogueMessageRequest(
    @Serializable(with = UUIDSerializer::class)
    override val id: UUID?,
    @Serializable(with = UUIDSerializer::class)
    val firstCompanionId: UUID?,
    @Serializable(with = UUIDSerializer::class)
    val secondCompanionId: UUID?,
    val text: String?
) : Request

@Serializable
data class DialogueMessageResponse(
    @Serializable(with = UUIDSerializer::class)
    override val id: UUID?,
    val sender: IdentityDependency?,
    val text: String?,
    val files: List<FileResponse>?,
    val created: String?
) : Response

@Serializable
data class ChatRequest(
    @Serializable(with = UUIDSerializer::class)
    override val id: UUID?,
    val name: String?,
    val description: String?,
    val opened: Boolean?,
    @Serializable(with = UUIDSerializer::class)
    val adminId: UUID?
) : Request

@Serializable
data class ChatDependency(
    @Serializable(with = UUIDSerializer::class)
    override val id: UUID?,
    val name: String?,
    val description: String?,
    val opened: Boolean?,
    val files: List<FileResponse>?
) : Dependency

@Serializable
data class ChatResponse(
    @Serializable(with = UUIDSerializer::class)
    override val id: UUID?,
    val name: String?,
    val description: String?,
    val opened: Boolean?,
    val files: List<FileResponse>?,
    val identities: List<IdentityDependency>?
) : Response

@Serializable
data class ChatMessageRequest(
    @Serializable(with = UUIDSerializer::class)
    override val id: UUID?,
    @Serializable(with = UUIDSerializer::class)
    val chatId: UUID?,
    @Serializable(with = UUIDSerializer::class)
    val senderId: UUID?,
    val text: String?
) : Request

@Serializable
data class ChatMessageDependency(
    @Serializable(with = UUIDSerializer::class)
    override val id: UUID?,
    val sender: IdentityDependency?,
    val text: String?,
    val files: List<FileResponse>?,
    val created: String?
) : Dependency

@Serializable
data class ChatMessageResponse(
    @Serializable(with = UUIDSerializer::class)
    override val id: UUID?,
    val chatDependency: ChatDependency?,
    val sender: IdentityDependency?,
    val text: String?,
    val files: List<FileResponse>?,
    val created: String?
) : Response

@Serializable
data class CommunityRequest(
    @Serializable(with = UUIDSerializer::class)
    override val id: UUID?,
    @Serializable(with = UUIDSerializer::class)
    val adminId: UUID?,
    val name: String?,
    val description: String?,
    val opened: Boolean?
) : Request

@Serializable
data class CommunityDependency(
    @Serializable(with = UUIDSerializer::class)
    override val id: UUID?,
    val name: String?,
    val description: String?,
    val opened: Boolean?,
    val files: List<FileResponse>?,
    val created: String?
) : Dependency

@Serializable
data class CommunityResponse(
    @Serializable(with = UUIDSerializer::class)
    override val id: UUID?,
    val name: String?,
    val description: String?,
    val opened: Boolean?,
    val files: List<FileResponse>?,
    val identities: List<IdentityDependency>?,
    val created: String?
) : Response

@Serializable
data class PublicationRequest(
    @Serializable(with = UUIDSerializer::class)
    override val id: UUID?,
    @Serializable(with = UUIDSerializer::class)
    val communityId: UUID?,
    @Serializable(with = UUIDSerializer::class)
    val senderId: UUID?,
    val text: String?
) : Request

@Serializable
data class PublicationDependency(
    @Serializable(with = UUIDSerializer::class)
    override val id: UUID?,
    val sender: IdentityDependency,
    val files: List<FileResponse>?,
    val text: String?,
    val created: String?
) : Dependency

@Serializable
data class PublicationResponse(
    @Serializable(with = UUIDSerializer::class)
    override val id: UUID?,
    val community: CommunityDependency?,
    val sender: IdentityDependency,
    val files: List<FileResponse>?,
    val text: String?,
    val created: String?
) : Response

@Serializable
data class CommentRequest(
    @Serializable(with = UUIDSerializer::class)
    override val id: UUID?,
    @Serializable(with = UUIDSerializer::class)
    val publicationId: UUID?,
    @Serializable(with = UUIDSerializer::class)
    val senderId: UUID?,
    val text: String?
) : Request

@Serializable
data class CommentDependency(
    @Serializable(with = UUIDSerializer::class)
    override val id: UUID?,
    val sender: IdentityDependency?,
    val files: List<FileResponse>?,
    val text: String?,
    val created: String?
) : Dependency

@Serializable
data class CommentResponse(
    @Serializable(with = UUIDSerializer::class)
    override val id: UUID?,
    val publication: PublicationDependency?,
    val sender: IdentityDependency?,
    val files: List<FileResponse>?,
    val text: String?,
    val created: String?
) : Response