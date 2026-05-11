package org.burgas.router

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.sessions.*
import io.ktor.util.*
import kotlinx.coroutines.Dispatchers
import org.burgas.dao.ChatEntity
import org.burgas.dao.IdentityEntity
import org.burgas.database.DatabaseConnection
import org.burgas.dto.AuthSession
import org.burgas.dto.ChatRequest
import org.burgas.dto.FileRequest
import org.burgas.dto.GroupRequest
import org.burgas.encryption.CipherManager
import org.burgas.service.ChatService
import org.jetbrains.exposed.dao.load
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import java.util.*

fun Application.configureChatRouter() {

    val chatService = ChatService()
    val urlsWithParam: List<String> = listOf(
        "/api/v1/chats/delete", "/api/v1/chats/upload-files", "/api/v1/chats/remove-files",
        "/api/v1/chats/create-preview-image", "/api/v1/chats/make-preview-image"
    )
    val urlsWithGroupBody: List<String> = listOf(
        "/api/v1/chats/join", "/api/v1/chats/out", "/api/v1/chats/remove-admin-status"
    )

    intercept(ApplicationCallPipeline.Plugins) {

        if (urlsWithParam.contains(call.request.path())) {
            val authSession = call.sessions.get(AuthSession::class)!!
            val chatId = UUID.fromString(call.parameters["chatId"])

            newSuspendedTransaction(db = DatabaseConnection.postgres, context = Dispatchers.Default, readOnly = true) {
                val chatEntity = ChatEntity.findById(chatId)!!
                    .load(ChatEntity::files, ChatEntity::identities, ChatEntity::messages)
                val identities = chatEntity.identities
                    .map { it.toIdentityInChat(chatEntity.id.value) }.filter { it.admin!! }

                if (identities.map { it.email }.contains(CipherManager.decrypt(authSession.token))) {
                    proceed()
                } else {
                    throw IllegalArgumentException("Identity not authorized")
                }
            }

        } else if (call.request.path().equals("/api/v1/chats/create", false)) {
            val authSession = call.sessions.get(AuthSession::class)!!
            val chatRequest = call.receive(ChatRequest::class)

            newSuspendedTransaction(db = DatabaseConnection.postgres, context = Dispatchers.Default, readOnly = true) {
                val identityEntity = IdentityEntity.findById(chatRequest.adminId!!)!!

                if (identityEntity.email == CipherManager.decrypt(authSession.token)) {
                    call.attributes[AttributeKey<ChatRequest>("chatRequest")] = chatRequest
                    proceed()
                } else {
                    throw IllegalArgumentException("Identity not authorized")
                }
            }

        } else if (call.request.path().equals("/api/v1/chats/update", false)) {
            val authSession = call.sessions.get(AuthSession::class)!!
            val chatRequest = call.receive(ChatRequest::class)

            newSuspendedTransaction(db = DatabaseConnection.postgres, context = Dispatchers.Default, readOnly = true) {
                val chatEntity = ChatEntity.findById(chatRequest.id!!)!!
                    .load(ChatEntity::files, ChatEntity::identities, ChatEntity::messages)
                val identities = chatEntity.identities
                    .map { it.toIdentityInChat(chatEntity.id.value) }.filter { it.admin!! }

                if (identities.map { it.email }.contains(CipherManager.decrypt(authSession.token))) {
                    call.attributes[AttributeKey<ChatRequest>("chatRequest")] = chatRequest
                    proceed()
                } else {
                    throw IllegalArgumentException("Identity not authorized")
                }
            }

        } else if (urlsWithGroupBody.contains(call.request.path())) {
            val authSession = call.sessions.get(AuthSession::class)!!
            val groupRequest = call.receive(GroupRequest::class)

            newSuspendedTransaction(db = DatabaseConnection.postgres, context = Dispatchers.Default, readOnly = true) {
                val identityEntity = IdentityEntity.findById(groupRequest.applicantId)!!

                if (identityEntity.email == CipherManager.decrypt(authSession.token)) {
                    call.attributes[AttributeKey<GroupRequest>("groupRequest")] = groupRequest
                    proceed()
                } else {
                    throw IllegalArgumentException("Identity not authorized")
                }
            }

        } else {
            proceed()
        }
    }

    routing {

        route("/api/v1/chats") {

            authenticate("basic-auth-session") {

                get("/by-id") {
                    val chatId = UUID.fromString(call.parameters["chatId"])
                    call.respond(HttpStatusCode.OK, chatService.findById(chatId))
                }

                post("/create") {
                    val chatRequest = call.attributes[AttributeKey<ChatRequest>("chatRequest")]
                    val chatResponse = chatService.create(chatRequest)
                    call.respondRedirect("/api/v1/chats/by-id?chatId=${chatResponse.id}")
                }

                post("/update") {
                    val chatRequest = call.attributes[AttributeKey<ChatRequest>("chatRequest")]
                    val chatResponse = chatService.update(chatRequest)
                    call.respondRedirect("/api/v1/chats/by-id?chatId=${chatResponse.id}")
                }

                delete("/delete") {
                    val chatId = UUID.fromString(call.parameters["chatId"])
                    chatService.delete(chatId)
                    call.respond(HttpStatusCode.OK)
                }

                put("/join") {
                    val groupRequest = call.attributes[AttributeKey<GroupRequest>("groupRequest")]
                    chatService.join(groupRequest)
                    call.respond(HttpStatusCode.OK)
                }

                put("/out") {
                    val groupRequest = call.attributes[AttributeKey<GroupRequest>("groupRequest")]
                    chatService.out(groupRequest)
                    call.respond(HttpStatusCode.OK)
                }

                put("/remove-admin-status") {
                    val groupRequest = call.attributes[AttributeKey<GroupRequest>("groupRequest")]
                    chatService.removeAdminStatus(groupRequest)
                    call.respond(HttpStatusCode.OK)
                }

                post("/upload-files") {
                    val chatId = UUID.fromString(call.parameters["chatId"])
                    val chatEntity = chatService.findEntity(chatId)
                    chatService.uploadFiles(chatEntity, call.receiveMultipart(Long.MAX_VALUE))
                    call.respond(HttpStatusCode.OK)
                }

                delete("/remove-files") {
                    val chatId = UUID.fromString(call.parameters["chatId"])
                    val fileRequest = call.receive(FileRequest::class)
                    val chatEntity = chatService.findEntity(chatId)
                    chatService.removeFiles(chatEntity, fileRequest)
                    call.respond(HttpStatusCode.OK)
                }

                post("/create-preview-image") {
                    val chatId = UUID.fromString(call.parameters["chatId"])
                    val chatEntity = chatService.findEntity(chatId)
                    chatService.createPreviewImage(chatEntity, call.receiveMultipart(Long.MAX_VALUE))
                    call.respond(HttpStatusCode.OK)
                }

                put("/make-preview-image") {
                    val chatId = UUID.fromString(call.parameters["chatId"])
                    val imageId = UUID.fromString(call.parameters["imageId"])
                    val chatEntity = chatService.findEntity(chatId)
                    chatService.makePreviewImage(chatEntity, imageId)
                    call.respond(HttpStatusCode.OK)
                }
            }
        }
    }
}