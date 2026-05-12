package org.burgas.service

import io.ktor.http.content.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.toList
import kotlinx.serialization.json.Json
import org.burgas.dao.CommunityEntity
import org.burgas.dao.FileEntity
import org.burgas.database.CommunityIdentityTable
import org.burgas.database.DatabaseConnection
import org.burgas.dto.CommunityRequest
import org.burgas.dto.CommunityResponse
import org.burgas.dto.FileRequest
import org.burgas.dto.GroupRequest
import org.burgas.redis.CacheKey
import org.burgas.redis.RedisCacheHandler
import org.burgas.service.dao.*
import org.jetbrains.exposed.dao.load
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import java.sql.Connection
import java.util.*

class CommunityService : RedisCacheHandler<CommunityEntity>, ReadDao<UUID, CommunityEntity, CommunityResponse>,
    DesignDao<UUID, CommunityRequest, CommunityResponse>, ModifyDao<CommunityRequest, CommunityResponse>, GroupDao,
    DesignFileDao<CommunityEntity>, DesignImageDao<UUID, CommunityEntity> {

    private val redis = DatabaseConnection.redis
    private val fileService = FileService()
    private val identityService = IdentityService()

    override suspend fun handleCache(entity: CommunityEntity) {
        val communityKey = CacheKey.COMMUNITY_KEY.format(entity.id.value)
        if (redis.exists(communityKey)) redis.del(communityKey)

        val identities = entity.identities
        if (!identities.empty()) {
            identities.forEach { identityEntity ->
                val identityKey = CacheKey.IDENTITY_KEY.format(identityEntity.id.value)
                if (redis.exists(identityKey)) redis.del(identityKey)
            }
        }

        val publications = entity.publications
        if (!publications.empty()) {
            publications.forEach { publicationEntity ->
                val publicationKey = CacheKey.PUBLICATION_KEY.format(publicationEntity.id.value)
                if (redis.exists(publicationKey)) redis.del(publicationKey)
            }
        }
    }

    override suspend fun findEntity(id: UUID): CommunityEntity = newSuspendedTransaction(
        db = DatabaseConnection.postgres, context = Dispatchers.Default, readOnly = true
    ) {
        CommunityEntity.findById(id)!!
            .load(CommunityEntity::files, CommunityEntity::identities, CommunityEntity::publications)
    }

    override suspend fun findById(id: UUID): CommunityResponse = newSuspendedTransaction(
        db = DatabaseConnection.postgres, context = Dispatchers.Default, readOnly = true
    ) {
        val communityKey = CacheKey.COMMUNITY_KEY.format(id)
        if (redis.exists(communityKey)) {
            Json.decodeFromString<CommunityResponse>(redis.get(communityKey))
        } else {
            val communityResponse = findEntity(id).toResponse()
            redis.set(communityKey, Json.encodeToString(communityResponse))
            communityResponse
        }
    }

    override suspend fun create(request: CommunityRequest): CommunityResponse = newSuspendedTransaction(
        db = DatabaseConnection.postgres,
        context = Dispatchers.Default,
        transactionIsolation = Connection.TRANSACTION_READ_COMMITTED
    ) {
        val communityEntity = CommunityEntity.new { this.insert(request) }
            .load(CommunityEntity::files, CommunityEntity::identities, CommunityEntity::publications)
        val identityEntity = identityService.findEntity(request.adminId!!)
        CommunityIdentityTable.insert {
            it[CommunityIdentityTable.communityId] = communityEntity.id.value
            it[CommunityIdentityTable.identityId] = identityEntity.id.value
            it[CommunityIdentityTable.admin] = true
        }
        handleCache(communityEntity)
        val communityKey = CacheKey.COMMUNITY_KEY.format(communityEntity.id.value)
        val communityResponse = communityEntity.toResponse()
        redis.set(communityKey, Json.encodeToString(communityResponse))
        communityResponse
    }

    override suspend fun update(request: CommunityRequest): CommunityResponse = newSuspendedTransaction(
        db = DatabaseConnection.postgres,
        context = Dispatchers.Default,
        transactionIsolation = Connection.TRANSACTION_READ_COMMITTED
    ) {
        val communityEntity = CommunityEntity.findByIdAndUpdate(request.id!!) { it.update(request) }!!
            .load(CommunityEntity::files, CommunityEntity::identities, CommunityEntity::publications)
        handleCache(communityEntity)
        val communityKey = CacheKey.COMMUNITY_KEY.format(communityEntity.id.value)
        val communityResponse = communityEntity.toResponse()
        redis.set(communityKey, Json.encodeToString(communityResponse))
        communityResponse
    }

    override suspend fun delete(id: UUID) = newSuspendedTransaction(
        db = DatabaseConnection.postgres,
        context = Dispatchers.Default,
        transactionIsolation = Connection.TRANSACTION_READ_COMMITTED
    ) {
        val communityEntity = findEntity(id)
        communityEntity.files.forEach { it.delete() }
        communityEntity.delete()
        handleCache(communityEntity)
    }

    override suspend fun join(groupRequest: GroupRequest) = newSuspendedTransaction(
        db = DatabaseConnection.postgres,
        context = Dispatchers.Default,
        transactionIsolation = Connection.TRANSACTION_READ_COMMITTED
    ) {
        val communityEntity = findEntity(groupRequest.groupId)
        val identityEntity = identityService.findEntity(groupRequest.applicantId)

        if (communityEntity.opened) {
            if (!communityEntity.identities.map { it.id.value }.contains(identityEntity.id.value)) {
                communityEntity.identities = SizedCollection(communityEntity.identities + identityEntity)
                handleCache(communityEntity)

            } else {
                throw IllegalArgumentException("This applicant already in community")
            }

        } else {
            throw IllegalArgumentException("Community is closed for joining")
        }
    }

    override suspend fun out(groupRequest: GroupRequest) = newSuspendedTransaction(
        db = DatabaseConnection.postgres,
        context = Dispatchers.Default,
        transactionIsolation = Connection.TRANSACTION_READ_COMMITTED
    ) {
        val communityEntity = findEntity(groupRequest.groupId)
        val identityEntity = identityService.findEntity(groupRequest.applicantId)

        if (communityEntity.identities.map { it.id.value }.contains(identityEntity.id.value)) {
            val communityIdentity = CommunityIdentityTable.selectAll()
                .where { (CommunityIdentityTable.communityId eq communityEntity.id.value) and
                        (CommunityIdentityTable.identityId eq identityEntity.id.value) }
                .single()

            if (!communityIdentity[CommunityIdentityTable.admin]) {
                CommunityIdentityTable.deleteWhere {
                    (CommunityIdentityTable.communityId eq communityIdentity[CommunityIdentityTable.communityId]) and
                            (CommunityIdentityTable.identityId eq communityIdentity[CommunityIdentityTable.identityId])
                }
                handleCache(communityEntity)

            } else {
                throw IllegalArgumentException("Admin can't out from community, you need to remove admin status first")
            }

        } else {
            throw IllegalArgumentException("This applicant not in community for out")
        }
    }

    override suspend fun removeAdminStatus(groupRequest: GroupRequest) = newSuspendedTransaction(
        db = DatabaseConnection.postgres,
        context = Dispatchers.Default,
        transactionIsolation = Connection.TRANSACTION_READ_COMMITTED
    ) {
        val communityEntity = findEntity(groupRequest.groupId)
        val identityEntity = identityService.findEntity(groupRequest.applicantId)

        if (communityEntity.identities.map { it.id.value }.contains(identityEntity.id.value)) {
            val operation = (CommunityIdentityTable.communityId eq communityEntity.id.value) and
                    (CommunityIdentityTable.identityId eq identityEntity.id.value)

            val communityIdentity = CommunityIdentityTable.selectAll().where { operation }.single()
            if (communityIdentity[CommunityIdentityTable.admin]) {
                CommunityIdentityTable.update(where = { operation }) { it[CommunityIdentityTable.admin] = false }
                handleCache(communityEntity)

            } else {
                throw IllegalArgumentException("Admin can't out from community, you need to become user first")
            }

        } else {
            throw IllegalArgumentException("This applicant not in community for out")
        }
    }

    override suspend fun uploadFiles(
        entity: CommunityEntity,
        multiPartData: MultiPartData
    ) = newSuspendedTransaction(
        db = DatabaseConnection.postgres,
        context = Dispatchers.Default,
        transactionIsolation = Connection.TRANSACTION_READ_COMMITTED
    ) {
        val fileEntities = multiPartData.asFlow().map { fileService.create(it) }.toList()
        entity.files = SizedCollection(entity.files + fileEntities)
        handleCache(entity)
    }

    override suspend fun removeFiles(entity: CommunityEntity, fileRequest: FileRequest) = newSuspendedTransaction(
        db = DatabaseConnection.postgres,
        context = Dispatchers.Default,
        transactionIsolation = Connection.TRANSACTION_READ_COMMITTED
    ) {
        FileEntity.forIds(fileRequest.fileIds).forEach { fileEntity ->
            if (entity.files.map { it.id.value }.contains(fileEntity.id.value)) fileEntity.delete()
        }
        handleCache(entity)
    }

    override suspend fun createPreviewImage(
        entity: CommunityEntity,
        multiPartData: MultiPartData
    ) = newSuspendedTransaction(
        db = DatabaseConnection.postgres,
        context = Dispatchers.Default,
        transactionIsolation = Connection.TRANSACTION_READ_COMMITTED
    ) {
        entity.files.filter { it.preview }.forEach { it.preview = false }
        val readPart = multiPartData.readPart()!!
        val fileEntity = fileService.createPreview(readPart)
        entity.files = SizedCollection(entity.files + fileEntity)
        handleCache(entity)
    }

    override suspend fun makePreviewImage(entity: CommunityEntity, imageId: UUID) = newSuspendedTransaction(
        db = DatabaseConnection.postgres,
        context = Dispatchers.Default,
        transactionIsolation = Connection.TRANSACTION_READ_COMMITTED
    ) {
        val fileEntity = fileService.findEntity(imageId)
        if (entity.files.map { it.id.value }.contains(fileEntity.id.value)) {
            entity.files.filter { it.preview }.forEach { it.preview = false }
            fileService.makePreview(fileEntity)
            handleCache(entity)
        } else {
            throw IllegalArgumentException("This file not included in this entity list")
        }
    }
}