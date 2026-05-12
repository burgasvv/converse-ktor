package org.burgas.service

import io.ktor.http.content.*
import kotlinx.coroutines.Dispatchers
import kotlinx.serialization.json.Json
import org.burgas.dao.ChatMessageEntity
import org.burgas.database.DatabaseConnection
import org.burgas.dto.ChatMessageRequest
import org.burgas.dto.ChatMessageResponse
import org.burgas.redis.CacheKey
import org.burgas.redis.RedisCacheHandler
import org.burgas.service.dao.DesignPartDao
import org.burgas.service.dao.ReadDao
import org.jetbrains.exposed.dao.load
import org.jetbrains.exposed.sql.SizedCollection
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import java.sql.Connection
import java.util.*

class ChatMessageService : RedisCacheHandler<ChatMessageEntity>, ReadDao<UUID, ChatMessageEntity, ChatMessageResponse>,
    DesignPartDao<UUID, ChatMessageRequest, ChatMessageResponse> {

    private val redis = DatabaseConnection.redis
    private val fileService = FileService()

    override suspend fun handleCache(entity: ChatMessageEntity) {
        val chatMessageKey = CacheKey.CHAT_MESSAGE_KEY.format(entity.id.value)
        if (redis.exists(chatMessageKey)) redis.del(chatMessageKey)

        val sender = entity.sender
        if (sender != null) {
            val senderKey = CacheKey.IDENTITY_KEY.format(sender.id.value)
            if (redis.exists(senderKey)) redis.del(senderKey)
        }

        val chat = entity.chat
        val chatKey = CacheKey.CHAT_KEY.format(chat.id.value)
        if (redis.exists(chatKey)) redis.del(chatKey)
    }

    override suspend fun findEntity(id: UUID): ChatMessageEntity = newSuspendedTransaction(
        db = DatabaseConnection.postgres, context = Dispatchers.Default, readOnly = true
    ) {
        ChatMessageEntity.findById(id)!!
            .load(ChatMessageEntity::chat, ChatMessageEntity::sender, ChatMessageEntity::files)
    }

    override suspend fun findById(id: UUID): ChatMessageResponse = newSuspendedTransaction(
        db = DatabaseConnection.postgres, context = Dispatchers.Default, readOnly = true
    ) {
        val chatMessageKey = CacheKey.CHAT_MESSAGE_KEY.format(id)
        if (redis.exists(chatMessageKey)) {
            Json.decodeFromString<ChatMessageResponse>(redis.get(chatMessageKey))
        } else {
            val chatMessageResponse = findEntity(id).toResponse()
            redis.set(chatMessageKey, Json.encodeToString(chatMessageResponse))
            chatMessageResponse
        }
    }

    override suspend fun create(
        entityRequest: ChatMessageRequest,
        parts: List<PartData>
    ): ChatMessageResponse = newSuspendedTransaction(
        db = DatabaseConnection.postgres,
        context = Dispatchers.Default,
        transactionIsolation = Connection.TRANSACTION_READ_COMMITTED
    ) {
        val chatMessageEntity = ChatMessageEntity.new { this.insert(entityRequest) }
            .load(ChatMessageEntity::chat, ChatMessageEntity::sender, ChatMessageEntity::files)
        val fileEntities = parts.map { fileService.create(it) }
        chatMessageEntity.files = SizedCollection(chatMessageEntity.files + fileEntities)
        handleCache(chatMessageEntity)
        val chatMessageKey = CacheKey.CHAT_MESSAGE_KEY.format(chatMessageEntity.id.value)
        val chatMessageResponse = chatMessageEntity.toResponse()
        redis.set(chatMessageKey, Json.encodeToString(chatMessageResponse))
        chatMessageResponse
    }

    override suspend fun delete(entityId: UUID) = newSuspendedTransaction(
        db = DatabaseConnection.postgres,
        context = Dispatchers.Default,
        transactionIsolation = Connection.TRANSACTION_READ_COMMITTED
    ) {
        val chatMessageEntity = findEntity(entityId)
        chatMessageEntity.files.forEach { it.delete() }
        chatMessageEntity.delete()
        handleCache(chatMessageEntity)
    }
}