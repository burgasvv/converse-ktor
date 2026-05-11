package org.burgas.service

import io.ktor.http.content.*
import kotlinx.coroutines.Dispatchers
import kotlinx.serialization.json.Json
import org.burgas.dao.DialogueMessageEntity
import org.burgas.database.DatabaseConnection
import org.burgas.dto.DialogueMessageRequest
import org.burgas.dto.DialogueMessageResponse
import org.burgas.redis.CacheKey
import org.burgas.redis.RedisCacheHandler
import org.burgas.service.dao.DesignPartDao
import org.burgas.service.dao.ReadDao
import org.jetbrains.exposed.dao.load
import org.jetbrains.exposed.sql.SizedCollection
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import java.sql.Connection
import java.util.*

class DialogueMessageService : RedisCacheHandler<DialogueMessageEntity>,
    ReadDao<UUID, DialogueMessageEntity, DialogueMessageResponse>,
    DesignPartDao<UUID, DialogueMessageRequest, DialogueMessageResponse> {

    private val redis = DatabaseConnection.redis
    private val fileService = FileService()

    override suspend fun handleCache(entity: DialogueMessageEntity) {
        val dialogueMessageKey = CacheKey.DIALOGUE_MESSAGE_KEY.format(entity.id.value)
        if (redis.exists(dialogueMessageKey)) redis.del(dialogueMessageKey)

        val sender = entity.sender
        if (sender != null) {
            val senderKey = CacheKey.IDENTITY_KEY.format(sender.id.value)
            if (redis.exists(senderKey)) redis.del(senderKey)
        }

        val dialogueKey = CacheKey.DIALOGUE_KEY.format(entity.dialogue.id.value)
        if (redis.exists(dialogueKey)) redis.del(dialogueKey)
    }

    override suspend fun findEntity(id: UUID): DialogueMessageEntity = newSuspendedTransaction(
        db = DatabaseConnection.postgres, context = Dispatchers.Default, readOnly = true
    ) {
        DialogueMessageEntity.findById(id)!!
            .load(DialogueMessageEntity::dialogue, DialogueMessageEntity::sender, DialogueMessageEntity::files)
    }

    override suspend fun findById(id: UUID): DialogueMessageResponse = newSuspendedTransaction(
        db = DatabaseConnection.postgres, context = Dispatchers.Default, readOnly = true
    ) {
        val dialogueMessageKey = CacheKey.DIALOGUE_MESSAGE_KEY.format(id)
        if (redis.exists(dialogueMessageKey)) {
            Json.decodeFromString<DialogueMessageResponse>(redis.get(dialogueMessageKey))
        } else {
            val dialogueMessageResponse = findEntity(id).toResponse()
            redis.set(dialogueMessageKey, Json.encodeToString(dialogueMessageResponse))
            dialogueMessageResponse
        }
    }

    override suspend fun create(
        entityRequest: DialogueMessageRequest,
        parts: List<PartData>
    ): DialogueMessageResponse = newSuspendedTransaction(
        db = DatabaseConnection.postgres,
        context = Dispatchers.Default,
        transactionIsolation = Connection.TRANSACTION_READ_COMMITTED
    ) {
        val dialogueMessageEntity = DialogueMessageEntity.new { this.insert(entityRequest) }
            .load(DialogueMessageEntity::dialogue, DialogueMessageEntity::sender, DialogueMessageEntity::files)
        val fileEntities = parts.map { fileService.create(it) }
        dialogueMessageEntity.files = SizedCollection(dialogueMessageEntity.files + fileEntities)
        handleCache(dialogueMessageEntity)
        val dialogueMessageKey = CacheKey.DIALOGUE_MESSAGE_KEY.format(dialogueMessageEntity.id.value)
        val dialogueMessageResponse = dialogueMessageEntity.toResponse()
        redis.set(dialogueMessageKey, Json.encodeToString(dialogueMessageResponse))
        dialogueMessageResponse
    }

    override suspend fun delete(entityId: UUID) = newSuspendedTransaction(
        db = DatabaseConnection.postgres,
        context = Dispatchers.Default,
        transactionIsolation = Connection.TRANSACTION_READ_COMMITTED
    ) {
        val dialogueMessageEntity = findEntity(entityId)
        dialogueMessageEntity.files.forEach { it.delete() }
        dialogueMessageEntity.delete()
        handleCache(dialogueMessageEntity)
    }
}