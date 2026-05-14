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
import org.burgas.dao.CommentEntity
import org.burgas.dao.IdentityEntity
import org.burgas.dao.PublicationEntity
import org.burgas.database.DatabaseConnection
import org.burgas.dto.AuthSession
import org.burgas.dto.CommentRequest
import org.burgas.dto.CommentResponse
import org.burgas.encryption.CipherManager
import org.burgas.service.CommentService
import org.jetbrains.exposed.dao.load
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import java.util.*
import java.util.concurrent.CopyOnWriteArraySet

fun Application.configureCommentRouter() {

    val commentService = CommentService()
    val webSocketConnections: CopyOnWriteArraySet<DefaultWebSocketServerSession> = CopyOnWriteArraySet()
    val urlsByCommentParam: List<String> = listOf("/api/v1/comments/by-id", "/api/v1/comments/delete")
    val urlsByPublicationParam: List<String> = listOf("/api/v1/comments/ws/by-publication")
    val urlsByMultipart: List<String> = listOf("/api/v1/comments/create")

    intercept(ApplicationCallPipeline.Plugins) {

        if (urlsByCommentParam.contains(call.request.path())) {
            val authSession = call.sessions.get(AuthSession::class)!!
            val commentId = UUID.fromString(call.parameters["commentId"])

            newSuspendedTransaction(
                db = DatabaseConnection.postgres, context = Dispatchers.Default, readOnly = true
            ) {
                val commentEntity = CommentEntity.findById(commentId)!!
                    .load(CommentEntity::files, CommentEntity::sender, CommentEntity::publication)
                val sender = commentEntity.sender
                if (sender != null && sender.email == CipherManager.decrypt(authSession.token)) {
                    proceed()
                } else if (
                    sender == null &&
                    commentEntity.publication.community.identities
                        .map { it.toIdentityInCommunity(commentEntity.publication.community.id.value) }
                        .filter { it.admin!! }
                        .map { it.email }.contains(CipherManager.decrypt(authSession.token))
                ) {
                    proceed()
                } else {
                    throw IllegalArgumentException("Identity not authorized")
                }
            }

        } else if (urlsByPublicationParam.contains(call.request.path())) {
            val authSession = call.sessions.get(AuthSession::class)!!
            val publicationId = UUID.fromString(call.parameters["publicationId"])

            newSuspendedTransaction(
                db = DatabaseConnection.postgres, context = Dispatchers.Default, readOnly = true
            ) {
                val publicationEntity = PublicationEntity.findById(publicationId)!!
                    .load(
                        PublicationEntity::files, PublicationEntity::comments,
                        PublicationEntity::sender, PublicationEntity::community
                    )
                if (publicationEntity.community.identities.map { it.email }
                    .contains(CipherManager.decrypt(authSession.token))) {
                    proceed()
                } else {
                    throw IllegalArgumentException("Identity not authorized")
                }
            }

        } else if (urlsByMultipart.contains(call.request.path())) {
            val authSession = call.sessions.get(AuthSession::class)!!
            val multiPartData = call.receiveMultipart(Long.MAX_VALUE)

            val formPart = multiPartData.asFlow().filterIsInstance<PartData.FormItem>().first()
            val commentRequest = Json.decodeFromString<CommentRequest>(formPart.value)

            newSuspendedTransaction(
                db = DatabaseConnection.postgres, context = Dispatchers.Default, readOnly = true
            ) {
                val identityEntity = IdentityEntity.findById(commentRequest.senderId!!)!!

                if (identityEntity.email == CipherManager.decrypt(authSession.token)) {
                    val files = multiPartData.asFlow().filterIsInstance<PartData.FileItem>().toList()
                    call.attributes[AttributeKey<CommentRequest>("commentRequest")] = commentRequest
                    call.attributes[AttributeKey<List<PartData>>("files")] = files
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

        route("/api/v1/comments") {

            authenticate("basic-auth-session") {

                webSocket("/ws/by-publication") {
                    webSocketConnections += this
                    try {
                        val publicationId = UUID.fromString(call.parameters["publicationId"])
                        newSuspendedTransaction(
                            db = DatabaseConnection.postgres, context = Dispatchers.Default, readOnly = true
                        ) {
                            PublicationEntity.findById(publicationId)!!.load(PublicationEntity::comments)
                                .comments.map { it.toResponse() }
                                .forEach { send(Frame.Text(Json.encodeToString(it))) }
                        }
                        this.incoming.receiveAsFlow().filterIsInstance<Frame.Text>().collect { frameText ->
                            val readText = frameText.readText()
                            val commentResponse = Json.decodeFromString<CommentResponse>(readText)
                            if (commentResponse.publication!!.id == publicationId) {
                                send(readText)
                            } else {
                                send("Wrong publication for this comment")
                            }
                        }

                    } finally {
                        webSocketConnections -= this
                    }
                }

                get("/by-id") {
                    val commentId = UUID.fromString(call.parameters["commentId"])
                    call.respond(HttpStatusCode.OK, commentService.findById(commentId))
                }

                post("/create") {
                    val commentRequest = call.attributes[AttributeKey<CommentRequest>("commentRequest")]
                    val files = call.attributes[AttributeKey<List<PartData>>("files")]
                    val commentResponse = commentService.create(commentRequest, files)
                    webSocketConnections.forEach { it.send(Frame.Text(Json.encodeToString(commentResponse))) }
                    call.respondRedirect("/api/v1/comments/by-id?commentId=${commentResponse.id}")
                }

                delete("/delete") {
                    val commentId = UUID.fromString(call.parameters["commentId"])
                    commentService.delete(commentId)
                    call.respond(HttpStatusCode.OK)
                }
            }
        }
    }
}