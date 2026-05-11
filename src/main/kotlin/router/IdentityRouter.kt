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
import org.burgas.dto.AuthSession
import org.burgas.dto.FileRequest
import org.burgas.dto.IdentityRequest
import org.burgas.service.IdentityService
import java.util.*

fun Application.configureIdentityRouter() {

    val identityService = IdentityService()

    val urlsWithParam: List<String> = listOf(
        "/api/v1/identities/delete", "/api/v1/identities/upload-files", "/api/v1/identities/remove-files",
        "/api/v1/identities/create-preview-image", "/api/v1/identities/make-preview-image",
        "/api/v1/identities/add-contact", "/api/v1/identities/remove-contact"
    )

    val urlsWithBody: List<String> = listOf("/api/v1/identities/update")

    routing {

        @Suppress("DEPRECATION")
        intercept(ApplicationCallPipeline.Call) {

            if (urlsWithBody.contains(call.request.path())) {
                val authSession = call.sessions.get(AuthSession::class) ?: throw IllegalArgumentException("Auth Session is null")
                val identityRequest = call.receive(IdentityRequest::class)
                val identityEntity = identityService.findEntity(identityRequest.id!!)

                if (identityEntity.email == authSession.email) {
                    call.attributes[AttributeKey<IdentityRequest>("identityRequest")] = identityRequest
                    proceed()
                } else {
                    throw IllegalArgumentException("Identity not authorized")
                }

            } else if (urlsWithParam.contains(call.request.path())) {
                val authSession = call.sessions.get(AuthSession::class) ?: throw IllegalArgumentException("Auth Session is null")
                val identityId = UUID.fromString(call.parameters["identityId"])
                val identityEntity = identityService.findEntity(identityId)

                if (identityEntity.email == authSession.email) {
                    proceed()
                } else {
                    throw IllegalArgumentException("Identity not authorized")
                }

            } else {
                proceed()
            }
        }

        route("/api/v1/identities") {

            get("/by-id") {
                val identityId = UUID.fromString(call.parameters["identityId"])
                call.respond(HttpStatusCode.OK, identityService.findById(identityId))
            }

            post("/create") {
                val identityRequest = call.receive(IdentityRequest::class)
                val identityResponse = identityService.create(identityRequest)
                call.respondRedirect("/api/v1/identities/by-id?identityId=${identityResponse.id}")
            }

            authenticate("basic-auth-admin") {

                get {
                    call.respond(HttpStatusCode.OK, identityService.findAll())
                }
            }

            authenticate("basic-auth-session") {

                post("/update") {
                    val identityRequest = call.attributes[AttributeKey<IdentityRequest>("identityRequest")]
                    val identityResponse = identityService.update(identityRequest)
                    call.respondRedirect("/api/v1/identities/by-id?identityId=${identityResponse.id}")
                }

                post("/delete") {
                    val identityId = UUID.fromString(call.parameters["identityId"])
                    identityService.delete(identityId)
                    call.respondRedirect("/api/v1/security/logout")
                }

                put("/upload-files") {
                    val identityId = UUID.fromString(call.parameters["identityId"])
                    val identityEntity = identityService.findEntity(identityId)
                    identityService.uploadFiles(identityEntity, call.receiveMultipart(Long.MAX_VALUE))
                    call.respond(HttpStatusCode.OK)
                }

                put("/remove-files") {
                    val identityId = UUID.fromString(call.parameters["identityId"])
                    val identityEntity = identityService.findEntity(identityId)
                    val fileRequest = call.receive(FileRequest::class)
                    identityService.removeFiles(identityEntity, fileRequest)
                    call.respond(HttpStatusCode.OK)
                }

                put("/create-preview-image") {
                    val identityId = UUID.fromString(call.parameters["identityId"])
                    val identityEntity = identityService.findEntity(identityId)
                    identityService.createPreviewImage(identityEntity, call.receiveMultipart(Long.MAX_VALUE))
                    call.respond(HttpStatusCode.OK)
                }

                put("/make-preview-image") {
                    val identityId = UUID.fromString(call.parameters["identityId"])
                    val imageId = UUID.fromString(call.parameters["imageId"])
                    val identityEntity = identityService.findEntity(identityId)
                    identityService.makePreviewImage(identityEntity, imageId)
                    call.respond(HttpStatusCode.OK)
                }

                put("/add-contact") {
                    val identityId = UUID.fromString(call.parameters["identityId"])
                    val contactId = UUID.fromString(call.parameters["contactId"])
                    identityService.addContact(identityId, contactId)
                    call.respond(HttpStatusCode.OK)
                }

                put("/remove-contact") {
                    val identityId = UUID.fromString(call.parameters["identityId"])
                    val contactId = UUID.fromString(call.parameters["contactId"])
                    identityService.removeContact(identityId, contactId)
                    call.respond(HttpStatusCode.OK)
                }
            }
        }
    }
}