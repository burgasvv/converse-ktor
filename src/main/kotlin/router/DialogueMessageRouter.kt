package org.burgas.router

import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.request.path
import io.ktor.server.request.receiveMultipart
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.sessions.get
import io.ktor.server.sessions.sessions
import io.ktor.server.websocket.*
import io.ktor.util.*
import io.ktor.websocket.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.toList
import kotlinx.serialization.json.Json
import org.burgas.dao.DialogueEntity
import org.burgas.dao.DialogueMessageEntity
import org.burgas.dao.IdentityEntity
import org.burgas.database.DatabaseConnection
import org.burgas.dto.AuthSession
import org.burgas.dto.DialogueMessageRequest
import org.burgas.dto.DialogueMessageResponse
import org.burgas.encryption.CipherManager
import org.burgas.service.DialogueMessageService
import org.jetbrains.exposed.dao.load
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import java.util.*
import java.util.concurrent.CopyOnWriteArraySet

fun Application.configureDialogueMessageRouter() {

    val dialogueMessageService = DialogueMessageService()
    val webSocketConnections: CopyOnWriteArraySet<DefaultWebSocketServerSession> = CopyOnWriteArraySet()

    val urlsByMessageParam: List<String> = listOf("/api/v1/dialogue-messages/by-id", "/api/v1/dialogue-messages/delete")
    val urlsByDialogueParam: List<String> = listOf("/api/v1/dialogue-messages/ws/by-dialogue")
    val urlsByMultipart: List<String> = listOf("/api/v1/dialogue-messages/create")

    intercept(ApplicationCallPipeline.Plugins) {

        if (urlsByMessageParam.contains(call.request.path())) {
            val authSession = call.sessions.get(AuthSession::class)!!
            val dialogueMessageId = UUID.fromString(call.parameters["dialogueMessageId"])

            newSuspendedTransaction(
                db = DatabaseConnection.postgres, context = Dispatchers.Default, readOnly = true
            ) {
                val dialogueMessageEntity = DialogueMessageEntity.findById(dialogueMessageId)!!
                    .load(
                        DialogueMessageEntity::dialogue,
                        DialogueMessageEntity::sender, DialogueMessageEntity::files
                    )
                val sender = dialogueMessageEntity.sender
                    ?: throw IllegalArgumentException("Dialogue message sender is null")
                if (sender.email == CipherManager.decrypt(authSession.token)) {
                    proceed()
                } else {
                    throw IllegalArgumentException("Identity not authorized")
                }
            }

        } else if (urlsByDialogueParam.contains(call.request.path())) {
            val authSession = call.sessions.get(AuthSession::class)!!
            val dialogueId = UUID.fromString(call.parameters["dialogueId"])

            newSuspendedTransaction(
                db = DatabaseConnection.postgres, context = Dispatchers.Default, readOnly = true
            ) {
                val dialogueEntity = DialogueEntity.findById(dialogueId)!!
                    .load(DialogueEntity::identities, DialogueEntity::messages)

                if (dialogueEntity.identities.map { it.email }
                        .contains(CipherManager.decrypt(authSession.token))) {
                    proceed()
                } else {
                    throw IllegalArgumentException("Identity not authorized")
                }
            }

        } else if (urlsByMultipart.contains(call.request.path())) {
            val authSession = call.sessions.get(AuthSession::class)!!
            val multiPartData = call.receiveMultipart(Long.MAX_VALUE)

            val readPart = multiPartData.asFlow().filterIsInstance<PartData.FormItem>().first()
            val dialogueMessageRequest = Json.decodeFromString<DialogueMessageRequest>(readPart.value)
            val identityEntity = newSuspendedTransaction(
                db = DatabaseConnection.postgres, context = Dispatchers.Default, readOnly = true
            ) {
                IdentityEntity.findById(dialogueMessageRequest.senderId!!)!!
            }
            if (identityEntity.email == CipherManager.decrypt(authSession.token)) {
                val fileItems = multiPartData.asFlow().filterIsInstance<PartData.FileItem>().toList()
                call.attributes[AttributeKey<DialogueMessageRequest>("dialogueMessageRequest")] = dialogueMessageRequest
                call.attributes[AttributeKey<List<PartData>>("files")] = fileItems
                proceed()
            } else {
                throw IllegalArgumentException("Identity not authorized")
            }

        } else {
            proceed()
        }
    }

    routing {

        route("/api/v1/dialogue-messages") {

            authenticate("basic-auth-session") {

                webSocket("/ws/by-dialogue") {
                    webSocketConnections += this
                    try {
                        val dialogueId = UUID.fromString(call.parameters["dialogueId"])
                        val dialogueMessageResponses = newSuspendedTransaction(
                            db = DatabaseConnection.postgres, context = Dispatchers.Default, readOnly = true
                        ) {
                            DialogueEntity.findById(dialogueId)!!
                                .load(DialogueEntity::identities, DialogueEntity::messages)
                                .messages.map { it.toResponse() }
                        }
                        dialogueMessageResponses.forEach { send(Frame.Text(Json.encodeToString(it))) }
                        this.incoming.receiveAsFlow().filterIsInstance<Frame.Text>()
                            .collect { frameText ->
                                val readText = frameText.readText()
                                val dialogueMessageResponse = Json.decodeFromString<DialogueMessageResponse>(readText)
                                if (dialogueMessageResponse.dialogue?.id == dialogueId) {
                                    send(Frame.Text(readText))
                                } else {
                                    send(Frame.Text("Wrong dialogue for this message"))
                                }
                            }

                    } finally {
                        webSocketConnections -= this
                    }
                }

                get("/by-id") {
                    val dialogueMessageId = UUID.fromString(call.parameters["dialogueMessageId"])
                    call.respond(HttpStatusCode.OK, dialogueMessageService.findById(dialogueMessageId))
                }

                post("/create") {
                    val dialogueMessageRequest = call.attributes[AttributeKey<DialogueMessageRequest>("dialogueMessageRequest")]
                    val files = call.attributes[AttributeKey<List<PartData>>("files")]
                    val dialogueMessageResponse = dialogueMessageService.create(dialogueMessageRequest, files)
                    webSocketConnections.forEach { it.send(Frame.Text(Json.encodeToString(dialogueMessageResponse))) }
                    call.respondRedirect("/api/v1/dialogue-messages/by-id?dialogueMessageId=${dialogueMessageResponse.id}")
                }

                delete("/delete") {
                    val dialogueMessageId = UUID.fromString(call.parameters["dialogueMessageId"])
                    dialogueMessageService.delete(dialogueMessageId)
                    call.respond(HttpStatusCode.OK)
                }
            }
        }
    }
}