package org.burgas.service

import kotlinx.coroutines.Dispatchers
import kotlinx.serialization.json.Json
import org.burgas.dao.DialogueEntity
import org.burgas.database.DatabaseConnection
import org.burgas.dto.DialogueResponse
import org.burgas.redis.CacheKey
import org.burgas.redis.RedisCacheHandler
import org.burgas.service.dao.ReadDao
import org.jetbrains.exposed.dao.load
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import java.sql.Connection
import java.util.*

class DialogueService : RedisCacheHandler<DialogueEntity>, ReadDao<UUID, DialogueEntity, DialogueResponse> {

    private val redis = DatabaseConnection.redis

    override suspend fun handleCache(entity: DialogueEntity) {
        TODO("Not yet implemented")
    }

    override suspend fun findEntity(id: UUID): DialogueEntity = newSuspendedTransaction(
        db = DatabaseConnection.postgres, context = Dispatchers.Default, readOnly = true
    ) {
        DialogueEntity.findById(id)!!.load(DialogueEntity::identities, DialogueEntity::messages)
    }

    override suspend fun findById(id: UUID): DialogueResponse = newSuspendedTransaction(
        db = DatabaseConnection.postgres, context = Dispatchers.Default, readOnly = true
    ) {
        val dialogueKey = CacheKey.DIALOGUE_KEY.format(id)
        if (redis.exists(dialogueKey)) {
            Json.decodeFromString<DialogueResponse>(redis.get(dialogueKey))
        } else {
            val dialogueResponse = findEntity(id).toResponse()
            redis.set(dialogueKey, Json.encodeToString(dialogueResponse))
            dialogueResponse
        }
    }

    suspend fun delete(id: UUID) = newSuspendedTransaction(
        db = DatabaseConnection.postgres,
        context = Dispatchers.Default,
        transactionIsolation = Connection.TRANSACTION_READ_COMMITTED
    ) {
        val dialogueEntity = findEntity(id)
        dialogueEntity.delete()
        handleCache(dialogueEntity)
    }
}