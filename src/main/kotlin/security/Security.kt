package org.burgas.security

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.plugins.cors.routing.*
import io.ktor.server.plugins.csrf.*
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.response.*
import io.ktor.server.sessions.*
import kotlinx.coroutines.Dispatchers
import org.burgas.dao.IdentityEntity
import org.burgas.database.Authority
import org.burgas.database.DatabaseConnection
import org.burgas.database.IdentityTable
import org.burgas.dto.AuthSession
import org.burgas.dto.CsrfToken
import org.burgas.dto.ExceptionResponse
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import org.mindrot.jbcrypt.BCrypt

fun Application.configureSecurity() {

    authentication {

        basic(name = "basic-auth-all") {
            validate { credentials ->
                val identityEntity = newSuspendedTransaction(
                    db = DatabaseConnection.postgres, context = Dispatchers.Default, readOnly = true
                ) {
                    IdentityEntity.find { IdentityTable.email eq credentials.name }.singleOrNull()
                }
                if (
                    identityEntity != null && identityEntity.status &&
                    BCrypt.checkpw(credentials.password, identityEntity.password)
                ) {
                    UserPasswordCredential(credentials.name, credentials.password)
                } else {
                    null
                }
            }
        }

        basic(name = "basic-auth-admin") {
            validate { credentials ->
                val identityEntity = newSuspendedTransaction(
                    db = DatabaseConnection.postgres, context = Dispatchers.Default, readOnly = true
                ) {
                    IdentityEntity.find { IdentityTable.email eq credentials.name }.singleOrNull()
                }
                if (
                    identityEntity != null && identityEntity.status &&
                    identityEntity.authority == Authority.ADMIN &&
                    BCrypt.checkpw(credentials.password, identityEntity.password)
                ) {
                    UserPasswordCredential(credentials.name, credentials.password)
                } else {
                    null
                }
            }
        }

        session<AuthSession>("basic-auth-session") {
            validate { authSession -> authSession }
            challenge {
                val exceptionResponse = ExceptionResponse(
                    status = HttpStatusCode.Unauthorized.description,
                    code = HttpStatusCode.Unauthorized.value,
                    message = "Not authenticated"
                )
                call.respond(HttpStatusCode.Unauthorized, exceptionResponse)
            }
        }
    }

    install(Sessions) {
        cookie<CsrfToken>("CSRF_TOKEN")
        cookie<AuthSession>("AUTH_SESSION")
    }

    install(StatusPages) {
        exception<Throwable> { call, cause ->
            val exceptionResponse = ExceptionResponse(
                status = HttpStatusCode.BadRequest.description,
                code = HttpStatusCode.BadRequest.value,
                message = cause.message
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