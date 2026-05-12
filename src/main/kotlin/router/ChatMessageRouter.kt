package org.burgas.router

import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.sessions.*
import io.ktor.server.websocket.*
import io.ktor.util.*
import io.ktor.websocket.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.toList
import kotlinx.serialization.json.Json
import org.burgas.dao.ChatEntity
import org.burgas.dao.ChatMessageEntity
import org.burgas.dao.IdentityEntity
import org.burgas.database.DatabaseConnection
import org.burgas.dto.AuthSession
import org.burgas.dto.ChatMessageRequest
import org.burgas.dto.ChatMessageResponse
import org.burgas.encryption.CipherManager
import org.burgas.service.ChatMessageService
import org.jetbrains.exposed.dao.load
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import java.util.*
import java.util.concurrent.CopyOnWriteArraySet

fun Application.configureChatMessageRouter() {

    val chatMessageService = ChatMessageService()
    val webSocketConnections: CopyOnWriteArraySet<DefaultWebSocketServerSession> = CopyOnWriteArraySet()
    val urlsByMessageParam: List<String> = listOf("/api/v1/chat-messages/by-id", "/api/v1/chat-messages/delete")
    val urlsByChatParam: List<String> = listOf("/api/v1/chat-messages/ws/by-chat")
    val urlsByMultipart: List<String> = listOf("/api/v1/chat-messages/create")

    intercept(ApplicationCallPipeline.Plugins) {

        if (urlsByMessageParam.contains(call.request.path())) {
            val authSession = call.sessions.get(AuthSession::class)!!
            val chatMessageId = UUID.fromString(call.parameters["chatMessageId"])

            newSuspendedTransaction(db = DatabaseConnection.postgres, context = Dispatchers.Default, readOnly = true) {
                val chatMessageEntity = ChatMessageEntity.findById(chatMessageId)!!
                    .load(ChatMessageEntity::chat, ChatMessageEntity::sender, ChatMessageEntity::files)

                if (chatMessageEntity.sender!!.email == CipherManager.decrypt(authSession.token)) {
                    proceed()
                } else {
                    throw IllegalArgumentException("Identity not authorized")
                }
            }

        } else if (urlsByChatParam.contains(call.request.path())) {
            val authSession = call.sessions.get(AuthSession::class)!!
            val chatId = UUID.fromString(call.parameters["chatId"])

            newSuspendedTransaction(db = DatabaseConnection.postgres, context = Dispatchers.Default, readOnly = true) {
                val chatEntity = ChatEntity.findById(chatId)!!
                    .load(ChatEntity::identities, ChatEntity::messages, ChatEntity::files)

                if (chatEntity.identities.map { it.email }.contains(CipherManager.decrypt(authSession.token))) {
                    proceed()
                } else {
                    throw IllegalArgumentException("Identity not authorized")
                }
            }

        } else if (urlsByMultipart.contains(call.request.path())) {
            val authSession = call.sessions.get(AuthSession::class)!!
            val multipart = call.receiveMultipart(Long.MAX_VALUE)

            val formItem = multipart.asFlow().filterIsInstance<PartData.FormItem>().first()
            val chatMessageRequest = Json.decodeFromString<ChatMessageRequest>(formItem.value)

            newSuspendedTransaction(db = DatabaseConnection.postgres, context = Dispatchers.Default, readOnly = true) {
                val identityEntity = IdentityEntity.findById(chatMessageRequest.senderId!!)!!

                if (identityEntity.email == CipherManager.decrypt(authSession.token)) {
                    val fileItems = multipart.asFlow().filterIsInstance<PartData.FileItem>().toList()
                    call.attributes[AttributeKey<ChatMessageRequest>("chatMessageRequest")] = chatMessageRequest
                    call.attributes[AttributeKey<List<PartData>>("files")] = fileItems
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

        route("/api/v1/chat-messages") {

            authenticate("basic-auth-session") {

                webSocket("/ws/by-chat") {
                    webSocketConnections += this
                    try {
                        val chatId = UUID.fromString(call.parameters["chatId"])
                        newSuspendedTransaction(
                            db = DatabaseConnection.postgres, context = Dispatchers.Default, readOnly = true
                        ) {
                            ChatEntity.findById(chatId)!!
                                .load(ChatEntity::files, ChatEntity::identities, ChatEntity::messages)
                                .messages.map { it.toResponse() }
                                .forEach { send(Frame.Text(Json.encodeToString(it))) }
                        }
                        this.incoming.receiveAsFlow().filterIsInstance<Frame.Text>().collect { frameText ->
                            val readText = frameText.readText()
                            val chatMessageResponse = Json.decodeFromString<ChatMessageResponse>(readText)
                            if (chatMessageResponse.chat!!.id == chatId) {
                                send(readText)
                            } else {
                                send("Wrong message for this chat")
                            }
                        }
                    } finally {
                        webSocketConnections -= this
                    }
                }

                get("/by-id") {
                    val chatMessageId = UUID.fromString(call.parameters["chatMessageId"])
                    call.respond(HttpStatusCode.OK, chatMessageService.findById(chatMessageId))
                }

                post("/create") {
                    val chatMessageRequest = call.attributes[AttributeKey<ChatMessageRequest>("chatMessageRequest")]
                    val files = call.attributes[AttributeKey<List<PartData>>("files")]
                    val chatMessageResponse = chatMessageService.create(chatMessageRequest, files)
                    webSocketConnections.forEach { it.send(Frame.Text(Json.encodeToString(chatMessageResponse))) }
                    call.respondRedirect("/api/v1/chat-messages/by-id?chatMessageId=${chatMessageResponse.id}")
                }

                delete("/delete") {
                    val chatMessageId = UUID.fromString(call.parameters["chatMessageId"])
                    chatMessageService.delete(chatMessageId)
                    call.respond(HttpStatusCode.OK)
                }
            }
        }
    }
}