package org.burgas.service

import io.ktor.http.content.*
import kotlinx.coroutines.Dispatchers
import kotlinx.serialization.json.Json
import org.burgas.dao.PublicationEntity
import org.burgas.database.DatabaseConnection
import org.burgas.dto.PublicationRequest
import org.burgas.dto.PublicationResponse
import org.burgas.redis.CacheKey
import org.burgas.redis.RedisCacheHandler
import org.burgas.service.dao.DesignPartDao
import org.burgas.service.dao.ReadDao
import org.jetbrains.exposed.dao.load
import org.jetbrains.exposed.sql.SizedCollection
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import java.sql.Connection
import java.util.*

class PublicationService : RedisCacheHandler<PublicationEntity>, ReadDao<UUID, PublicationEntity, PublicationResponse>,
    DesignPartDao<UUID, PublicationRequest, PublicationResponse> {

    private val redis = DatabaseConnection.redis
    private val fileService = FileService()

    override suspend fun handleCache(entity: PublicationEntity) {
        val publicationKey = CacheKey.PUBLICATION_KEY.format(entity.id.value)
        if (redis.exists(publicationKey)) redis.del(publicationKey)

        val sender = entity.sender
        if (sender != null) {
            val senderKey = CacheKey.IDENTITY_KEY.format(sender.id.value)
            if (redis.exists(senderKey)) redis.del(senderKey)
        }

        val community = entity.community
        val communityKey = CacheKey.COMMUNITY_KEY.format(community.id.value)
        if (redis.exists(communityKey)) redis.del(communityKey)

        val comments = entity.comments
        if (!comments.empty()) {
            comments.forEach { commentEntity ->
                val commentKey = CacheKey.COMMENT_KEY.format(commentEntity.id.value)
                if (redis.exists(commentKey)) redis.del(commentKey)
            }
        }
    }

    override suspend fun findEntity(id: UUID): PublicationEntity = newSuspendedTransaction(
        db = DatabaseConnection.postgres, context = Dispatchers.Default, readOnly = true
    ) {
        PublicationEntity.findById(id)!!
            .load(
                PublicationEntity::comments,
                PublicationEntity::files,
                PublicationEntity::community,
                PublicationEntity::sender
            )
    }

    override suspend fun findById(id: UUID): PublicationResponse = newSuspendedTransaction(
        db = DatabaseConnection.postgres, context = Dispatchers.Default, readOnly = true
    ) {
        val publicationKey = CacheKey.PUBLICATION_KEY.format(id)
        if (redis.exists(publicationKey)) {
            Json.decodeFromString<PublicationResponse>(redis.get(publicationKey))
        } else {
            val publicationResponse = findEntity(id).toResponse()
            redis.set(publicationKey, Json.encodeToString(publicationResponse))
            publicationResponse
        }
    }

    override suspend fun create(
        entityRequest: PublicationRequest,
        parts: List<PartData>
    ): PublicationResponse = newSuspendedTransaction(
        db = DatabaseConnection.postgres,
        context = Dispatchers.Default,
        transactionIsolation = Connection.TRANSACTION_READ_COMMITTED
    ) {
        val publicationEntity = PublicationEntity.new { this.insert(entityRequest) }
            .load(
                PublicationEntity::comments,
                PublicationEntity::files,
                PublicationEntity::community,
                PublicationEntity::sender
            )
        val fileEntities = parts.map { fileService.create(it) }
        publicationEntity.files = SizedCollection(publicationEntity.files + fileEntities)
        handleCache(publicationEntity)
        val publicationKey = CacheKey.PUBLICATION_KEY.format(publicationEntity.id.value)
        val publicationResponse = publicationEntity.toResponse()
        redis.set(publicationKey, Json.encodeToString(publicationResponse))
        publicationResponse
    }

    override suspend fun delete(entityId: UUID) = newSuspendedTransaction(
        db = DatabaseConnection.postgres,
        context = Dispatchers.Default,
        transactionIsolation = Connection.TRANSACTION_READ_COMMITTED
    ) {
        val publicationEntity = findEntity(entityId)
        publicationEntity.files.forEach { it.delete() }
        publicationEntity.delete()
        handleCache(publicationEntity)
    }
}