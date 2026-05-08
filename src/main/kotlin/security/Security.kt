package org.burgas.security

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.plugins.cors.routing.CORS
import io.ktor.server.plugins.csrf.CSRF
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.response.*
import io.ktor.server.sessions.*
import org.burgas.dto.CsrfToken
import org.burgas.dto.ExceptionResponse

fun Application.configureSecurity() {

    install(Sessions) {
        cookie<CsrfToken>("CSRF_TOKEN")
    }

    install(StatusPages) {
        exception<Throwable> { call, cause ->
            val exceptionResponse = ExceptionResponse(
                status = HttpStatusCode.BadRequest.description,
                code = HttpStatusCode.BadRequest.value,
                message = cause.localizedMessage
            )
            call.respond(HttpStatusCode.BadRequest, exceptionResponse)
        }
    }

    install(CORS) {
        allowMethod(HttpMethod.Options)
        allowMethod(HttpMethod.Put)
        allowMethod(HttpMethod.Delete)
        allowMethod(HttpMethod.Patch)
        allowMethod(HttpMethod.Get)
        allowMethod(HttpMethod.Post)

        allowHeader(HttpHeaders.Host)
        allowHeader(HttpHeaders.Origin)
        allowHeader(HttpHeaders.Accept)
        allowHeader(HttpHeaders.ContentType)
        allowHeader(HttpHeaders.Authorization)
        allowHeader("X-CSRF-Token")

        allowHost("localhost:4200")
        allowOrigins { origin -> origin.equals("http://localhost:4200", false) }
    }

    install(CSRF) {
        allowOrigin("http://localhost:9000")
        originMatchesHost()
        checkHeader("X-CSRF-Token")
    }
}