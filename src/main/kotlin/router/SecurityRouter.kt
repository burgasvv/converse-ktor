package org.burgas.router

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.sessions.*
import org.burgas.dto.AuthSession
import org.burgas.dto.CsrfToken
import org.burgas.encryption.CipherManager
import java.util.*

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

                get("/login") {
                    val authSession = call.sessions.get(AuthSession::class)
                    if (authSession != null) {
                        call.respond(HttpStatusCode.OK, "You already logged in, need to logout")
                    } else {
                        val principal = call.principal<UserPasswordCredential>()!!
                        call.sessions.set(AuthSession(CipherManager.encrypt(principal.name)), AuthSession::class)
                        call.respond(HttpStatusCode.OK, "Successfully logged in")
                    }
                }
            }

            authenticate("basic-auth-session") {

                get("/logout") {
                    call.sessions.clear(AuthSession::class)
                    call.respond(HttpStatusCode.OK, "Successfully logged out")
                }
            }
        }
    }
}