package org.burgas.router

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.auth.UserPasswordCredential
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.principal
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import io.ktor.server.sessions.clear
import io.ktor.server.sessions.get
import io.ktor.server.sessions.sessions
import io.ktor.server.sessions.set
import org.burgas.dto.AuthSession
import org.burgas.dto.CsrfToken
import java.util.UUID

fun Application.configureSecurityRouter() {
    routing {

        route("/api/v1/security") {

            get("/csrf-token") {
                val csrfToken = call.sessions.get(CsrfToken::class)
                if (csrfToken != null) {
                    call.respond(HttpStatusCode.OK, csrfToken)
                } else {
                    val newCsrfToken = CsrfToken(value = UUID.randomUUID())
                    call.sessions.set(newCsrfToken, CsrfToken::class)
                    call.respond(HttpStatusCode.OK, newCsrfToken)
                }
            }

            authenticate("basic-auth-all") {

                post("/login") {
                    val authSession = call.sessions.get(AuthSession::class)
                    if (authSession != null) {
                        call.respond(HttpStatusCode.OK, "You already logged in, need to logout")
                    } else {
                        val principal = call.principal<UserPasswordCredential>()!!
                        call.sessions.set(AuthSession(principal.name), AuthSession::class)
                        call.respond(HttpStatusCode.OK, "Successfully logged in")
                    }
                }
            }

            authenticate("basic-auth-session") {

                post("/logout") {
                    call.sessions.clear(AuthSession::class)
                    call.respond(HttpStatusCode.OK, "Successfully logged out")
                }
            }
        }
    }
}