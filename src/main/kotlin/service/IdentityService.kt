package org.burgas.service

import io.ktor.http.content.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.toList
import kotlinx.serialization.json.Json
import org.burgas.dao.FileEntity
import org.burgas.dao.IdentityEntity
import org.burgas.database.DatabaseConnection
import org.burgas.database.IdentityContactTable
import org.burgas.dto.FileRequest
import org.burgas.dto.IdentityRequest
import org.burgas.dto.IdentityResponse
import org.burgas.redis.CacheKey
import org.burgas.redis.RedisCacheHandler
import org.burgas.service.dao.*
import org.jetbrains.exposed.dao.load
import org.jetbrains.exposed.dao.with
import org.jetbrains.exposed.sql.SizedCollection
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import java.sql.Connection
import java.util.*

class IdentityService : RedisCacheHandler<IdentityEntity>, ReadDao<UUID, IdentityEntity, IdentityResponse>,
    ListDao<IdentityResponse>, DesignDao<UUID, IdentityRequest, IdentityResponse>,
    ModifyDao<IdentityRequest, IdentityResponse>, DesignFileDao<IdentityEntity>, DesignImageDao<UUID, IdentityEntity> {

    private val redis = DatabaseConnection.redis
    private val fileService = FileService()

    override suspend fun handleCache(entity: IdentityEntity) {
        val identityKey = CacheKey.IDENTITY_KEY.format(entity.id.value)
        if (redis.exists(identityKey)) redis.del(identityKey)

        val contacts = entity.contacts
        if (!contacts.empty()) {
            contacts.forEach { identityEntity ->
                val contactKey = CacheKey.IDENTITY_KEY.format(identityEntity.id.value)
                if (redis.exists(contactKey)) redis.del(contactKey)
            }
        }

        val dialogues = entity.dialogues
        if (!dialogues.empty()) {
            dialogues.forEach { dialogueEntity ->
                val dialogueKey = CacheKey.DIALOGUE_KEY.format(dialogueEntity.id.value)
                if (redis.exists(dialogueKey)) redis.del(dialogueKey)
            }
        }

        val chats = entity.chats
        if (!chats.empty()) {
            chats.forEach { chatEntity ->
                val chatKey = CacheKey.CHAT_KEY.format(chatEntity.id.value)
                if (redis.exists(chatKey)) redis.del(chatKey)
            }
        }

        val communities = entity.communities
        if (!communities.empty()) {
            communities.forEach { communityEntity ->
                val communityKey = CacheKey.COMMUNITY_KEY.format(communityEntity.id.value)
                if (redis.exists(communityKey)) redis.del(communityKey)
            }
        }
    }

    override suspend fun findEntity(id: UUID): IdentityEntity = newSuspendedTransaction(
        db = DatabaseConnection.postgres, context = Dispatchers.Default, readOnly = true
    ) {
        IdentityEntity.findById(id)!!
            .load(
                IdentityEntity::files, IdentityEntity::contacts,
                IdentityEntity::dialogues, IdentityEntity::chats, IdentityEntity::communities
            )
    }

    override suspend fun findById(id: UUID): IdentityResponse = newSuspendedTransaction(
        db = DatabaseConnection.postgres, context = Dispatchers.Default, readOnly = true
    ) {
        val identityKey = CacheKey.IDENTITY_KEY.format(id)
        if (redis.exists(identityKey)) {
            Json.decodeFromString<IdentityResponse>(redis.get(identityKey))
        } else {
            val identityResponse = findEntity(id).toResponse()
            redis.set(identityKey, Json.encodeToString(identityResponse))
            identityResponse
        }
    }

    override suspend fun findAll(): List<IdentityResponse> = newSuspendedTransaction(
        db = DatabaseConnection.postgres, context = Dispatchers.Default, readOnly = true
    ) {
        IdentityEntity.all()
            .with(
                IdentityEntity::files, IdentityEntity::contacts,
                IdentityEntity::dialogues, IdentityEntity::chats, IdentityEntity::communities
            )
            .map { it.toResponse() }
    }

    override suspend fun create(request: IdentityRequest): IdentityResponse = newSuspendedTransaction(
        db = DatabaseConnection.postgres,
        context = Dispatchers.Default,
        transactionIsolation = Connection.TRANSACTION_READ_COMMITTED
    ) {
        val identityEntity = IdentityEntity.new { this.insert(request) }
            .load(
                IdentityEntity::files, IdentityEntity::contacts,
                IdentityEntity::dialogues, IdentityEntity::chats, IdentityEntity::communities
            )
        val identityKey = CacheKey.IDENTITY_KEY.format(identityEntity.id.value)
        val identityResponse = identityEntity.toResponse()
        redis.set(identityKey, Json.encodeToString(identityResponse))
        identityResponse
    }

    override suspend fun update(request: IdentityRequest): IdentityResponse = newSuspendedTransaction(
        db = DatabaseConnection.postgres,
        context = Dispatchers.Default,
        transactionIsolation = Connection.TRANSACTION_READ_COMMITTED
    ) {
        val identityEntity = IdentityEntity.findByIdAndUpdate(request.id!!) { it.update(request) }!!
            .load(
                IdentityEntity::files, IdentityEntity::contacts,
                IdentityEntity::dialogues, IdentityEntity::chats, IdentityEntity::communities
            )
        handleCache(identityEntity)
        val identityKey = CacheKey.IDENTITY_KEY.format(identityEntity.id.value)
        val identityResponse = identityEntity.toResponse()
        redis.set(identityKey, Json.encodeToString(identityResponse))
        identityResponse
    }

    override suspend fun delete(id: UUID) = newSuspendedTransaction(
        db = DatabaseConnection.postgres,
        context = Dispatchers.Default,
        transactionIsolation = Connection.TRANSACTION_READ_COMMITTED
    ) {
        val identityEntity = findEntity(id)
        identityEntity.files.forEach { it.delete() }
        identityEntity.delete()
        handleCache(identityEntity)
    }

    override suspend fun uploadFiles(entity: IdentityEntity, multiPartData: MultiPartData) = newSuspendedTransaction(
        db = DatabaseConnection.postgres,
        context = Dispatchers.Default,
        transactionIsolation = Connection.TRANSACTION_READ_COMMITTED
    ) {
        val parts = multiPartData.asFlow().map { partData -> fileService.create(partData) }.toList()
        entity.files = SizedCollection(entity.files + parts)
        handleCache(entity)
    }

    override suspend fun removeFiles(entity: IdentityEntity, fileRequest: FileRequest) = newSuspendedTransaction(
        db = DatabaseConnection.postgres,
        context = Dispatchers.Default,
        transactionIsolation = Connection.TRANSACTION_READ_COMMITTED
    ) {
        FileEntity.forIds(fileRequest.fileIds).forEach { fileEntity ->
            if (entity.files.map { it.id.value }.contains(fileEntity.id.value)) fileEntity.delete()
        }
        handleCache(entity)
    }

    override suspend fun createPreviewImage(entity: IdentityEntity, multiPartData: MultiPartData) =
        newSuspendedTransaction(
            db = DatabaseConnection.postgres,
            context = Dispatchers.Default,
            transactionIsolation = Connection.TRANSACTION_READ_COMMITTED
        ) {
            val partData = multiPartData.readPart()!!
            entity.files.filter { it.preview }.forEach { it.preview = false }
            val fileEntity = fileService.createPreview(partData)
            entity.files = SizedCollection(entity.files + fileEntity)
            handleCache(entity)
        }

    override suspend fun makePreviewImage(entity: IdentityEntity, imageId: UUID) = newSuspendedTransaction(
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
            throw IllegalArgumentException("File not in identity file list")
        }
    }

    suspend fun addContact(identityId: UUID, contactId: UUID) = newSuspendedTransaction(
        db = DatabaseConnection.postgres,
        context = Dispatchers.Default,
        transactionIsolation = Connection.TRANSACTION_READ_COMMITTED
    ) {
        if (identityId == contactId) throw IllegalArgumentException("Wrong contact id")
        val identityEntity = findEntity(identityId)
        val contactEntity = findEntity(contactId)
        identityEntity.contacts = SizedCollection(identityEntity.contacts + contactEntity)
        contactEntity.contacts = SizedCollection(contactEntity.contacts + identityEntity)
        handleCache(identityEntity)
        handleCache(contactEntity)
    }

    suspend fun removeContact(identityId: UUID, contactId: UUID) = newSuspendedTransaction(
        db = DatabaseConnection.postgres,
        context = Dispatchers.Default,
        transactionIsolation = Connection.TRANSACTION_READ_COMMITTED
    ) {
        if (identityId == contactId) throw IllegalArgumentException("Wrong contact id")
        val identityEntity = findEntity(identityId)
        val contactEntity = findEntity(contactId)
        IdentityContactTable.deleteWhere {
            (IdentityContactTable.identityId eq identityEntity.id.value) and
                    (IdentityContactTable.contactId eq contactEntity.id.value)
        }
        IdentityContactTable.deleteWhere {
            (IdentityContactTable.identityId eq contactEntity.id.value) and
                    (IdentityContactTable.contactId eq identityEntity.id.value)
        }
        handleCache(identityEntity)
        handleCache(contactEntity)
    }
}