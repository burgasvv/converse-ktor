package org.burgas.router

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.sessions.get
import io.ktor.server.sessions.sessions
import io.ktor.util.*
import kotlinx.coroutines.Dispatchers
import org.burgas.dao.CommunityEntity
import org.burgas.dao.IdentityEntity
import org.burgas.database.DatabaseConnection
import org.burgas.dto.AuthSession
import org.burgas.dto.CommunityRequest
import org.burgas.dto.FileRequest
import org.burgas.dto.GroupRequest
import org.burgas.encryption.CipherManager
import org.burgas.service.CommunityService
import org.jetbrains.exposed.dao.load
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import java.util.*

fun Application.configureCommunityRouter() {

    val communityService = CommunityService()
    val urlsWithParam: List<String> = listOf(
        "/api/v1/communities/delete", "/api/v1/communities/upload-files", "/api/v1/communities/remove-files",
        "/api/v1/communities/create-preview-image", "/api/v1/communities/make-preview-image"
    )
    val urlsWithGroupBody: List<String> = listOf(
        "/api/v1/communities/join", "/api/v1/communities/out", "/api/v1/communities/remove-admin-status"
    )

    intercept(ApplicationCallPipeline.Plugins) {

        if (urlsWithParam.contains(call.request.path())) {
            val authSession = call.sessions.get(AuthSession::class)!!
            val chatId = UUID.fromString(call.parameters["communityId"])

            newSuspendedTransaction(db = DatabaseConnection.postgres, context = Dispatchers.Default, readOnly = true) {
                val communityEntity = CommunityEntity.findById(chatId)!!
                    .load(CommunityEntity::files, CommunityEntity::identities, CommunityEntity::publications)
                val identities = communityEntity.identities
                    .map { it.toIdentityInCommunity(communityEntity.id.value) }.filter { it.admin!! }

                if (identities.map { it.email }.contains(CipherManager.decrypt(authSession.token))) {
                    proceed()
                } else {
                    throw IllegalArgumentException("Identity not authorized")
                }
            }

        } else if (call.request.path().equals("/api/v1/communities/create", false)) {
            val authSession = call.sessions.get(AuthSession::class)!!
            val communityRequest = call.receive(CommunityRequest::class)

            newSuspendedTransaction(db = DatabaseConnection.postgres, context = Dispatchers.Default, readOnly = true) {
                val identityEntity = IdentityEntity.findById(communityRequest.adminId!!)!!

                if (identityEntity.email == CipherManager.decrypt(authSession.token)) {
                    call.attributes[AttributeKey<CommunityRequest>("communityRequest")] = communityRequest
                    proceed()
                } else {
                    throw IllegalArgumentException("Identity not authorized")
                }
            }

        } else if (call.request.path().equals("/api/v1/communities/update", false)) {
            val authSession = call.sessions.get(AuthSession::class)!!
            val communityRequest = call.receive(CommunityRequest::class)

            newSuspendedTransaction(db = DatabaseConnection.postgres, context = Dispatchers.Default, readOnly = true) {
                val communityEntity = CommunityEntity.findById(communityRequest.id!!)!!
                    .load(CommunityEntity::files, CommunityEntity::identities, CommunityEntity::publications)
                val identities = communityEntity.identities
                    .map { it.toIdentityInCommunity(communityEntity.id.value) }.filter { it.admin!! }

                if (identities.map { it.email }.contains(CipherManager.decrypt(authSession.token))) {
                    call.attributes[AttributeKey<CommunityRequest>("communityRequest")] = communityRequest
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

        route("/api/v1/communities") {

            authenticate("basic-auth-session") {

                get("/by-id") {
                    val communityId = UUID.fromString(call.parameters["communityId"])
                    call.respond(HttpStatusCode.OK, communityService.findById(communityId))
                }

                post("/create") {
                    val communityRequest = call.attributes[AttributeKey<CommunityRequest>("communityRequest")]
                    val communityResponse = communityService.create(communityRequest)
                    call.respondRedirect("/api/v1/communities/by-id?communityId=${communityResponse.id}")
                }

                post("/update") {
                    val communityRequest = call.attributes[AttributeKey<CommunityRequest>("communityRequest")]
                    val communityResponse = communityService.update(communityRequest)
                    call.respondRedirect("/api/v1/communities/by-id?communityId=${communityResponse.id}")
                }

                delete("/delete") {
                    val communityId = UUID.fromString(call.parameters["communityId"])
                    communityService.delete(communityId)
                    call.respond(HttpStatusCode.OK)
                }

                put("/join") {
                    val groupRequest = call.attributes[AttributeKey<GroupRequest>("groupRequest")]
                    communityService.join(groupRequest)
                    call.respond(HttpStatusCode.OK)
                }

                put("/out") {
                    val groupRequest = call.attributes[AttributeKey<GroupRequest>("groupRequest")]
                    communityService.out(groupRequest)
                    call.respond(HttpStatusCode.OK)
                }

                put("/remove-admin-status") {
                    val groupRequest = call.attributes[AttributeKey<GroupRequest>("groupRequest")]
                    communityService.removeAdminStatus(groupRequest)
                    call.respond(HttpStatusCode.OK)
                }

                post("/upload-files") {
                    val communityId = UUID.fromString(call.parameters["communityId"])
                    val communityEntity = communityService.findEntity(communityId)
                    communityService.uploadFiles(communityEntity, call.receiveMultipart(Long.MAX_VALUE))
                    call.respond(HttpStatusCode.OK)
                }

                delete("/remove-files") {
                    val communityId = UUID.fromString(call.parameters["communityId"])
                    val fileRequest = call.receive(FileRequest::class)
                    val communityEntity = communityService.findEntity(communityId)
                    communityService.removeFiles(communityEntity, fileRequest)
                    call.respond(HttpStatusCode.OK)
                }

                post("/create-preview-image") {
                    val communityId = UUID.fromString(call.parameters["communityId"])
                    val communityEntity = communityService.findEntity(communityId)
                    communityService.createPreviewImage(communityEntity, call.receiveMultipart(Long.MAX_VALUE))
                    call.respond(HttpStatusCode.OK)
                }

                put("/make-preview-image") {
                    val communityId = UUID.fromString(call.parameters["communityId"])
                    val communityEntity = communityService.findEntity(communityId)
                    val imageId = UUID.fromString(call.parameters["imageId"])
                    communityService.makePreviewImage(communityEntity, imageId)
                    call.respond(HttpStatusCode.OK)
                }
            }
        }
    }
}