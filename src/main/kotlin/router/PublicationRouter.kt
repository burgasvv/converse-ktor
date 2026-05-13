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
import org.burgas.dao.CommunityEntity
import org.burgas.dao.IdentityEntity
import org.burgas.dao.PublicationEntity
import org.burgas.database.DatabaseConnection
import org.burgas.dto.AuthSession
import org.burgas.dto.PublicationRequest
import org.burgas.dto.PublicationResponse
import org.burgas.encryption.CipherManager
import org.burgas.service.PublicationService
import org.jetbrains.exposed.dao.load
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import java.util.*
import java.util.concurrent.CopyOnWriteArraySet

fun Application.configurePublicationRouter() {

    val webSocketConnections: CopyOnWriteArraySet<DefaultWebSocketServerSession> = CopyOnWriteArraySet()
    val publicationService = PublicationService()
    val urlsByPublicationParam: List<String> = listOf("/api/v1/publications/by-id", "/api/v1/publications/delete")
    val urlsByCommunityParam: List<String> = listOf("/api/v1/publications/ws/by-community")
    val urlsByMultipart: List<String> = listOf("/api/v1/publications/create")

    intercept(ApplicationCallPipeline.Plugins) {

        if (urlsByPublicationParam.contains(call.request.path())) {
            val authSession = call.sessions.get(AuthSession::class)!!
            val publicationId = UUID.fromString(call.parameters["publicationId"])

            newSuspendedTransaction(
                db = DatabaseConnection.postgres, context = Dispatchers.Default, readOnly = true
            ) {
                val publicationEntity = PublicationEntity.findById(publicationId)!!
                    .load(
                        PublicationEntity::files, PublicationEntity::sender,
                        PublicationEntity::community, PublicationEntity::comments
                    )
                val sender = publicationEntity.sender
                if (sender != null && sender.email == CipherManager.decrypt(authSession.token)) {
                    proceed()
                } else if (
                    sender == null &&
                    publicationEntity.community.identities
                        .map { it.toIdentityInCommunity(publicationEntity.community.id.value) }
                        .filter { it.admin!! }
                        .map { it.email }.contains(CipherManager.decrypt(authSession.token))
                ) {
                    proceed()
                } else {
                    throw IllegalArgumentException("Identity not authorized")
                }
            }

        } else if (urlsByCommunityParam.contains(call.request.path())) {
            val authSession = call.sessions.get(AuthSession::class)!!
            val communityId = UUID.fromString(call.parameters["communityId"])

            newSuspendedTransaction(
                db = DatabaseConnection.postgres, context = Dispatchers.Default, readOnly = true
            ) {
                val communityEntity = CommunityEntity.findById(communityId)!!
                    .load(CommunityEntity::files, CommunityEntity::identities, CommunityEntity::publications)
                if (communityEntity.identities.map { it.email }.contains(CipherManager.decrypt(authSession.token))) {
                    proceed()
                } else {
                    throw IllegalArgumentException("Identity not authorized")
                }
            }

        } else if (urlsByMultipart.contains(call.request.path())) {
            val authSession = call.sessions.get(AuthSession::class)!!
            val multiPartData = call.receiveMultipart(Long.MAX_VALUE)

            val formPart = multiPartData.asFlow().filterIsInstance<PartData.FormItem>().first()
            val publicationRequest = Json.decodeFromString<PublicationRequest>(formPart.value)

            newSuspendedTransaction(
                db = DatabaseConnection.postgres, context = Dispatchers.Default, readOnly = true
            ) {
                val identityEntity = IdentityEntity.findById(publicationRequest.senderId!!)!!

                if (identityEntity.email == CipherManager.decrypt(authSession.token)) {
                    val files = multiPartData.asFlow().filterIsInstance<PartData.FileItem>().toList()
                    call.attributes[AttributeKey<PublicationRequest>("publicationRequest")] = publicationRequest
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

        route("/api/v1/publications") {

            authenticate("basic-auth-session") {

                webSocket("/ws/by-community") {
                    webSocketConnections += this
                    try {
                        val communityId = UUID.fromString(call.parameters["communityId"])
                        newSuspendedTransaction(
                            db = DatabaseConnection.postgres, context = Dispatchers.Default, readOnly = true
                        ) {
                            CommunityEntity.findById(communityId)!!
                                .load(CommunityEntity::publications)
                                .publications.map { it.toResponse() }
                                .forEach { send(Frame.Text(Json.encodeToString(it))) }
                        }
                        this.incoming.receiveAsFlow().filterIsInstance<Frame.Text>().collect { frameText ->
                            val readText = frameText.readText()
                            val publicationResponse = Json.decodeFromString<PublicationResponse>(readText)
                            if (publicationResponse.community!!.id == communityId) {
                                send(readText)
                            } else {
                                send("Wrong community for this publication")
                            }
                        }

                    } finally {
                        webSocketConnections -= this
                    }
                }

                get("/by-id") {
                    val publicationId = UUID.fromString(call.parameters["publicationId"])
                    call.respond(HttpStatusCode.OK, publicationService.findById(publicationId))
                }

                post("/create") {
                    val publicationRequest = call.attributes[AttributeKey<PublicationRequest>("publicationRequest")]
                    val files = call.attributes[AttributeKey<List<PartData>>("files")]
                    val publicationResponse = publicationService.create(publicationRequest, files)
                    webSocketConnections.forEach { it.send(Frame.Text(Json.encodeToString(publicationResponse))) }
                    call.respondRedirect("/api/v1/publications/by-id?publicationId=${publicationResponse.id}")
                }

                delete("/delete") {
                    val publicationId = UUID.fromString(call.parameters["publicationId"])
                    publicationService.delete(publicationId)
                    call.respond(HttpStatusCode.OK)
                }
            }
        }
    }
}