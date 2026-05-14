package org.burgas.service

import io.ktor.http.content.PartData
import kotlinx.coroutines.Dispatchers
import kotlinx.serialization.json.Json
import org.burgas.dao.CommentEntity
import org.burgas.database.DatabaseConnection
import org.burgas.dto.CommentRequest
import org.burgas.dto.CommentResponse
import org.burgas.redis.CacheKey
import org.burgas.redis.RedisCacheHandler
import org.burgas.service.dao.DesignPartDao
import org.burgas.service.dao.ReadDao
import org.jetbrains.exposed.dao.load
import org.jetbrains.exposed.sql.SizedCollection
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import java.sql.Connection
import java.util.UUID

class CommentService : RedisCacheHandler<CommentEntity>, ReadDao<UUID, CommentEntity, CommentResponse>,
    DesignPartDao<UUID, CommentRequest, CommentResponse> {

    private val redis = DatabaseConnection.redis
    private val fileService = FileService()

    override suspend fun handleCache(entity: CommentEntity) {
        val commentKey = CacheKey.COMMENT_KEY.format(entity.id.value)
        if (redis.exists(commentKey)) redis.del(commentKey)

        val sender = entity.sender
        if (sender != null) {
            val senderKey = CacheKey.IDENTITY_KEY.format(sender.id.value)
            if (redis.exists(senderKey)) redis.del(senderKey)
        }

        val publication = entity.publication
        val publicationKey = CacheKey.PUBLICATION_KEY.format(publication.id.value)
        if (redis.exists(publicationKey)) redis.del(publicationKey)
    }

    override suspend fun findEntity(id: UUID): CommentEntity = newSuspendedTransaction(
        db = DatabaseConnection.postgres, context = Dispatchers.Default, readOnly = true
    ) {
        CommentEntity.findById(id)!!
            .load(CommentEntity::sender, CommentEntity::publication, CommentEntity::files)
    }

    override suspend fun findById(id: UUID): CommentResponse = newSuspendedTransaction(
        db = DatabaseConnection.postgres, context = Dispatchers.Default, readOnly = true
    ) {
        val commentKey = CacheKey.COMMENT_KEY.format(id)
        if (redis.exists(commentKey)) {
            Json.decodeFromString<CommentResponse>(redis.get(commentKey))
        } else {
            val commentResponse = findEntity(id).toResponse()
            redis.set(commentKey, Json.encodeToString(commentResponse))
            commentResponse
        }
    }

    override suspend fun create(
        entityRequest: CommentRequest,
        parts: List<PartData>
    ): CommentResponse = newSuspendedTransaction(
        db = DatabaseConnection.postgres,
        context = Dispatchers.Default,
        transactionIsolation = Connection.TRANSACTION_READ_COMMITTED
    ) {
        val commentEntity = CommentEntity.new { this.insert(entityRequest) }
            .load(CommentEntity::sender, CommentEntity::publication, CommentEntity::files)
        val fileEntities = parts.map { fileService.create(it) }
        commentEntity.files = SizedCollection(commentEntity.files + fileEntities)
        handleCache(commentEntity)
        val commentKey = CacheKey.COMMENT_KEY.format(commentEntity.id.value)
        val commentResponse = commentEntity.toResponse()
        redis.set(commentKey, Json.encodeToString(commentResponse))
        commentResponse
    }

    override suspend fun delete(entityId: UUID) = newSuspendedTransaction(
        db = DatabaseConnection.postgres,
        context = Dispatchers.Default,
        transactionIsolation = Connection.TRANSACTION_READ_COMMITTED
    ) {
        val commentEntity = findEntity(entityId)
        commentEntity.files.forEach { it.delete() }
        commentEntity.delete()
        handleCache(commentEntity)
    }
}