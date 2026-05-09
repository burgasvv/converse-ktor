package org.burgas.router

import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.response.respondBytes
import io.ktor.server.routing.get
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import org.burgas.service.FileService
import java.util.UUID

fun Application.configureFileRouter() {

    val fileService = FileService()

    routing {

        route("/api/v1/files") {

            get("/by-id") {
                val fileId = UUID.fromString(call.parameters["fileId"])
                val findEntity = fileService.findEntity(fileId)
                call.respondBytes(ContentType.parse(findEntity.contentType), HttpStatusCode.OK) {
                    findEntity.data.bytes
                }
            }
        }
    }
}