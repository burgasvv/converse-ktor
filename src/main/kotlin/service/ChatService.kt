package org.burgas.service

import io.ktor.http.content.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.toList
import kotlinx.serialization.json.Json
import org.burgas.dao.ChatEntity
import org.burgas.dao.FileEntity
import org.burgas.database.ChatIdentityTable
import org.burgas.database.DatabaseConnection
import org.burgas.dto.ChatRequest
import org.burgas.dto.ChatResponse
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

class ChatService : RedisCacheHandler<ChatEntity>, ReadDao<UUID, ChatEntity, ChatResponse>,
    DesignDao<UUID, ChatRequest, ChatResponse>, ModifyDao<ChatRequest, ChatResponse>, GroupDao,
    DesignFileDao<ChatEntity>, DesignImageDao<UUID, ChatEntity> {

    private val redis = DatabaseConnection.redis
    private val identityService = IdentityService()
    private val filesService = FileService()

    override suspend fun handleCache(entity: ChatEntity) {
        val chatKey = CacheKey.CHAT_KEY.format(entity.id.value)
        if (redis.exists(chatKey)) redis.del(chatKey)

        val identities = entity.identities
        if (!identities.empty()) {
            identities.forEach { identityEntity ->
                val identityKey = CacheKey.IDENTITY_KEY.format(identityEntity.id.value)
                if (redis.exists(identityKey)) redis.del(identityKey)
            }
        }

        val messages = entity.messages
        if (!messages.empty()) {
            messages.forEach { chatMessageEntity ->
                val chaMessageKey = CacheKey.CHAT_MESSAGE_KEY.format(chatMessageEntity.id.value)
                if (redis.exists(chaMessageKey)) redis.del(chaMessageKey)
            }
        }
    }

    override suspend fun findEntity(id: UUID): ChatEntity = newSuspendedTransaction(
        db = DatabaseConnection.postgres, context = Dispatchers.Default, readOnly = true
    ) {
        ChatEntity.findById(id)!!
            .load(ChatEntity::files, ChatEntity::identities, ChatEntity::messages)
    }

    override suspend fun findById(id: UUID): ChatResponse = newSuspendedTransaction(
        db = DatabaseConnection.postgres, context = Dispatchers.Default, readOnly = true
    ) {
        val chatKey = CacheKey.CHAT_KEY.format(id)
        if (redis.exists(chatKey)) {
            Json.decodeFromString<ChatResponse>(redis.get(chatKey))
        } else {
            val chatResponse = findEntity(id).toResponse()
            redis.set(chatKey, Json.encodeToString(chatResponse))
            chatResponse
        }
    }

    override suspend fun create(request: ChatRequest): ChatResponse = newSuspendedTransaction(
        db = DatabaseConnection.postgres,
        context = Dispatchers.Default,
        transactionIsolation = Connection.TRANSACTION_READ_COMMITTED
    ) {
        val chatEntity = ChatEntity.new { this.insert(request) }
            .load(ChatEntity::files, ChatEntity::identities, ChatEntity::messages)
        val identityEntity = identityService.findEntity(request.adminId!!)

        ChatIdentityTable.insert {
            it[ChatIdentityTable.chatId] = chatEntity.id.value
            it[ChatIdentityTable.identityId] = identityEntity.id.value
            it[ChatIdentityTable.admin] = true
        }

        handleCache(chatEntity)
        val chatKey = CacheKey.CHAT_KEY.format(chatEntity.id.value)
        val chatResponse = chatEntity.toResponse()
        redis.set(chatKey, Json.encodeToString(chatResponse))
        chatResponse
    }

    override suspend fun update(request: ChatRequest): ChatResponse = newSuspendedTransaction(
        db = DatabaseConnection.postgres,
        context = Dispatchers.Default,
        transactionIsolation = Connection.TRANSACTION_READ_COMMITTED
    ) {
        val chatEntity = ChatEntity.findByIdAndUpdate(request.id!!) { it.update(request) }!!
            .load(ChatEntity::files, ChatEntity::identities, ChatEntity::messages)
        handleCache(chatEntity)
        val chatKey = CacheKey.CHAT_KEY.format(chatEntity.id.value)
        val chatResponse = chatEntity.toResponse()
        redis.set(chatKey, Json.encodeToString(chatResponse))
        chatResponse
    }

    override suspend fun delete(id: UUID) = newSuspendedTransaction(
        db = DatabaseConnection.postgres,
        context = Dispatchers.Default,
        transactionIsolation = Connection.TRANSACTION_READ_COMMITTED
    ) {
        val chatEntity = findEntity(id)
        chatEntity.files.forEach { it.delete() }
        chatEntity.delete()
        handleCache(chatEntity)
    }

    override suspend fun join(groupRequest: GroupRequest) = newSuspendedTransaction(
        db = DatabaseConnection.postgres,
        context = Dispatchers.Default,
        transactionIsolation = Connection.TRANSACTION_READ_COMMITTED
    ) {
        val chatEntity = findEntity(groupRequest.groupId)
        val identityEntity = identityService.findEntity(groupRequest.applicantId)

        if (chatEntity.opened) {
            if (!chatEntity.identities.map { it.id.value }.contains(identityEntity.id.value)) {
                chatEntity.identities = SizedCollection(chatEntity.identities + identityEntity)
                handleCache(chatEntity)

            } else {
                throw IllegalArgumentException("This applicant already in chat")
            }

        } else {
            throw IllegalArgumentException("Chat is closed for joining")
        }
    }

    override suspend fun out(groupRequest: GroupRequest) = newSuspendedTransaction(
        db = DatabaseConnection.postgres,
        context = Dispatchers.Default,
        transactionIsolation = Connection.TRANSACTION_READ_COMMITTED
    ) {
        val chatEntity = findEntity(groupRequest.groupId)
        val identityEntity = identityService.findEntity(groupRequest.applicantId)

        if (chatEntity.identities.map { it.id.value }.contains(identityEntity.id.value)) {
            val chatIdentity = ChatIdentityTable.selectAll()
                .where { (ChatIdentityTable.chatId eq chatEntity.id.value) and (ChatIdentityTable.identityId eq identityEntity.id.value) }
                .single()

            if (!chatIdentity[ChatIdentityTable.admin]) {
                ChatIdentityTable.deleteWhere {
                    (ChatIdentityTable.chatId eq chatIdentity[ChatIdentityTable.chatId]) and
                            (ChatIdentityTable.identityId eq chatIdentity[ChatIdentityTable.identityId])
                }
                handleCache(chatEntity)

            } else {
                throw IllegalArgumentException("Admin can't out from chat, you need to remove admin status first")
            }

        } else {
            throw IllegalArgumentException("This applicant not in chat for out")
        }
    }

    override suspend fun removeAdminStatus(groupRequest: GroupRequest) = newSuspendedTransaction(
        db = DatabaseConnection.postgres,
        context = Dispatchers.Default,
        transactionIsolation = Connection.TRANSACTION_READ_COMMITTED
    ) {
        val chatEntity = findEntity(groupRequest.groupId)
        val identityEntity = identityService.findEntity(groupRequest.applicantId)

        if (chatEntity.identities.map { it.id.value }.contains(identityEntity.id.value)) {
            val operation = (ChatIdentityTable.chatId eq chatEntity.id.value) and
                    (ChatIdentityTable.identityId eq identityEntity.id.value)

            val chatIdentity = ChatIdentityTable.selectAll().where { operation }.single()
            if (chatIdentity[ChatIdentityTable.admin]) {
                ChatIdentityTable.update(where = { operation }) { it[ChatIdentityTable.admin] = false }
                handleCache(chatEntity)

            } else {
                throw IllegalArgumentException("Admin can't out from chat, you need to become user first")
            }

        } else {
            throw IllegalArgumentException("This applicant not in chat for out")
        }
    }

    override suspend fun uploadFiles(
        entity: ChatEntity,
        multiPartData: MultiPartData
    ) = newSuspendedTransaction(
        db = DatabaseConnection.postgres,
        context = Dispatchers.Default,
        transactionIsolation = Connection.TRANSACTION_READ_COMMITTED
    ) {
        val fileEntities = multiPartData.asFlow().filterIsInstance<PartData.FileItem>()
                .map { filesService.create(it) }.toList()
        entity.files = SizedCollection(entity.files + fileEntities)
        handleCache(entity)
    }

    override suspend fun removeFiles(entity: ChatEntity, fileRequest: FileRequest) = newSuspendedTransaction(
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
        entity: ChatEntity,
        multiPartData: MultiPartData
    ) = newSuspendedTransaction(
        db = DatabaseConnection.postgres,
        context = Dispatchers.Default,
        transactionIsolation = Connection.TRANSACTION_READ_COMMITTED
    ) {
        entity.files.filter { it.preview }.forEach { it.preview = false }
        val readPart = multiPartData.readPart()!!
        val fileEntity = filesService.createPreview(readPart)
        entity.files = SizedCollection(entity.files + fileEntity)
        handleCache(entity)
    }

    override suspend fun makePreviewImage(entity: ChatEntity, imageId: UUID) = newSuspendedTransaction(
        db = DatabaseConnection.postgres,
        context = Dispatchers.Default,
        transactionIsolation = Connection.TRANSACTION_READ_COMMITTED
    ) {
        val fileEntity = filesService.findEntity(imageId)
        if (entity.files.map { it.id.value }.contains(fileEntity.id.value)) {
            entity.files.filter { it.preview }.forEach { it.preview = false }
            filesService.makePreview(fileEntity)
            handleCache(entity)
        } else {
            throw IllegalArgumentException("This file not include to this entity")
        }
    }
}