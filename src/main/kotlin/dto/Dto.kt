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
data class FileDependency(
    @Serializable(with = UUIDSerializer::class)
    override val id: UUID? = null,
    val name: String? = null,
    val contentType: String? = null,
    val preview: Boolean? = null
) : Dependency

@Serializable
data class IdentityRequest(
    @Serializable(with = UUIDSerializer::class)
    override val id: UUID? = null,
    val authority: Authority? = null,
    val username: String? = null,
    val password: String? = null,
    val email: String? = null,
    val phone: String? = null,
    val status: Boolean? = null,
    val firstname: String? = null,
    val lastname: String? = null,
    val patronymic: String? = null
): Request

@Serializable
data class IdentityDependency(
    @Serializable(with = UUIDSerializer::class)
    override val id: UUID? = null,
    val username: String? = null,
    val email: String? = null,
    val phone: String? = null,
    val firstname: String? = null,
    val lastname: String? = null,
    val patronymic: String? = null,
    val admin: Boolean? = null,
    val files: List<FileDependency>? = null
): Dependency

@Serializable
data class IdentityResponse(
    @Serializable(with = UUIDSerializer::class)
    override val id: UUID? = null,
    val username: String? = null,
    val email: String? = null,
    val phone: String? = null,
    val firstname: String? = null,
    val lastname: String? = null,
    val patronymic: String? = null,
    val files: List<FileDependency>? = null,
    val contacts: List<IdentityDependency>? = null,
    val dialogues: List<DialogueDependency>? = null,
    val chats: List<ChatDependency>? = null,
    val communities: List<CommunityDependency>? = null
): Response

@Serializable
data class DialogueDependency(
    @Serializable(with = UUIDSerializer::class)
    override val id: UUID? = null,
    val identities: List<IdentityDependency>? = null,
    val created: String? = null
) : Dependency

@Serializable
data class DialogueResponse(
    @Serializable(with = UUIDSerializer::class)
    override val id: UUID? = null,
    val identities: List<IdentityDependency>? = null,
    val messages: List<DialogueMessageDependency>? = null,
    val created: String? = null
) : Response

@Serializable
data class DialogueMessageRequest(
    @Serializable(with = UUIDSerializer::class)
    override val id: UUID? = null,
    @Serializable(with = UUIDSerializer::class)
    val dialogueId: UUID? = null,
    @Serializable(with = UUIDSerializer::class)
    val firstCompanionId: UUID? = null,
    @Serializable(with = UUIDSerializer::class)
    val secondCompanionId: UUID? = null,
    @Serializable(with = UUIDSerializer::class)
    val senderId: UUID? = null,
    val text: String? = null
) : Request

@Serializable
data class DialogueMessageDependency(
    @Serializable(with = UUIDSerializer::class)
    override val id: UUID? = null,
    val sender: IdentityDependency? = null,
    val text: String? = null,
    val files: List<FileDependency>? = null,
    val created: String? = null
) : Dependency

@Serializable
data class DialogueMessageResponse(
    @Serializable(with = UUIDSerializer::class)
    override val id: UUID? = null,
    val dialogue: DialogueDependency? = null,
    val sender: IdentityDependency? = null,
    val text: String? = null,
    val files: List<FileDependency>? = null,
    val created: String? = null
) : Response

@Serializable
data class ChatRequest(
    @Serializable(with = UUIDSerializer::class)
    override val id: UUID? = null,
    val name: String? = null,
    val description: String? = null,
    val opened: Boolean? = null,
    @Serializable(with = UUIDSerializer::class)
    val adminId: UUID? = null
) : Request

@Serializable
data class ChatDependency(
    @Serializable(with = UUIDSerializer::class)
    override val id: UUID? = null,
    val name: String? = null,
    val description: String? = null,
    val opened: Boolean? = null,
    val created: String? = null,
    val files: List<FileDependency>? = null
) : Dependency

@Serializable
data class ChatResponse(
    @Serializable(with = UUIDSerializer::class)
    override val id: UUID? = null,
    val name: String? = null,
    val description: String? = null,
    val opened: Boolean? = null,
    val created: String? = null,
    val files: List<FileDependency>? = null,
    val identities: List<IdentityDependency>? = null
) : Response

@Serializable
data class ChatMessageRequest(
    @Serializable(with = UUIDSerializer::class)
    override val id: UUID? = null,
    @Serializable(with = UUIDSerializer::class)
    val chatId: UUID? = null,
    @Serializable(with = UUIDSerializer::class)
    val senderId: UUID? = null,
    val text: String? = null
) : Request

@Serializable
data class ChatMessageDependency(
    @Serializable(with = UUIDSerializer::class)
    override val id: UUID? = null,
    val sender: IdentityDependency? = null,
    val text: String? = null,
    val created: String? = null,
    val files: List<FileDependency>? = null
) : Dependency

@Serializable
data class ChatMessageResponse(
    @Serializable(with = UUIDSerializer::class)
    override val id: UUID? = null,
    val chatDependency: ChatDependency? = null,
    val sender: IdentityDependency? = null,
    val text: String? = null,
    val created: String? = null,
    val files: List<FileDependency>? = null
) : Response

@Serializable
data class CommunityRequest(
    @Serializable(with = UUIDSerializer::class)
    override val id: UUID? = null,
    @Serializable(with = UUIDSerializer::class)
    val adminId: UUID? = null,
    val name: String? = null,
    val description: String? = null,
    val opened: Boolean? = null
) : Request

@Serializable
data class CommunityDependency(
    @Serializable(with = UUIDSerializer::class)
    override val id: UUID? = null,
    val name: String? = null,
    val description: String? = null,
    val opened: Boolean? = null,
    val created: String? = null,
    val files: List<FileDependency>? = null
) : Dependency

@Serializable
data class CommunityResponse(
    @Serializable(with = UUIDSerializer::class)
    override val id: UUID? = null,
    val name: String? = null,
    val description: String? = null,
    val opened: Boolean? = null,
    val created: String? = null,
    val files: List<FileDependency>? = null,
    val identities: List<IdentityDependency>? = null
) : Response

@Serializable
data class PublicationRequest(
    @Serializable(with = UUIDSerializer::class)
    override val id: UUID? = null,
    @Serializable(with = UUIDSerializer::class)
    val communityId: UUID? = null,
    @Serializable(with = UUIDSerializer::class)
    val senderId: UUID? = null,
    val text: String? = null
) : Request

@Serializable
data class PublicationDependency(
    @Serializable(with = UUIDSerializer::class)
    override val id: UUID? = null,
    val sender: IdentityDependency? = null,
    val text: String? = null,
    val created: String? = null,
    val files: List<FileDependency>? = null
) : Dependency

@Serializable
data class PublicationResponse(
    @Serializable(with = UUIDSerializer::class)
    override val id: UUID? = null,
    val community: CommunityDependency? = null,
    val sender: IdentityDependency? = null,
    val text: String? = null,
    val created: String? = null,
    val files: List<FileDependency>? = null
) : Response

@Serializable
data class CommentRequest(
    @Serializable(with = UUIDSerializer::class)
    override val id: UUID? = null,
    @Serializable(with = UUIDSerializer::class)
    val publicationId: UUID? = null,
    @Serializable(with = UUIDSerializer::class)
    val senderId: UUID? = null,
    val text: String? = null
) : Request

@Serializable
data class CommentDependency(
    @Serializable(with = UUIDSerializer::class)
    override val id: UUID? = null,
    val sender: IdentityDependency? = null,
    val text: String? = null,
    val created: String? = null,
    val files: List<FileDependency>? = null
) : Dependency

@Serializable
data class CommentResponse(
    @Serializable(with = UUIDSerializer::class)
    override val id: UUID? = null,
    val publication: PublicationDependency? = null,
    val sender: IdentityDependency? = null,
    val text: String? = null,
    val created: String? = null,
    val files: List<FileDependency>? = null
) : Response