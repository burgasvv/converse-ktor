package org.burgas.router

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.request.path
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.sessions.get
import io.ktor.server.sessions.sessions
import kotlinx.coroutines.Dispatchers
import org.burgas.dao.DialogueEntity
import org.burgas.database.DatabaseConnection
import org.burgas.dto.AuthSession
import org.burgas.encryption.CipherManager
import org.burgas.service.DialogueService
import org.jetbrains.exposed.dao.load
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import java.util.*

fun Application.configureDialogueRouter() {

    val dialogueService = DialogueService()

    val urlsByParam: List<String> = listOf("/api/v1/dialogues/by-id", "/api/v1/dialogues/delete")

    intercept(ApplicationCallPipeline.Plugins) {

        if (urlsByParam.contains(call.request.path())) {
            val authSession = call.sessions.get(AuthSession::class)
                ?: throw IllegalArgumentException("Auth Session is null")
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

        } else {
            proceed()
        }
    }

    routing {

        route("/api/v1/dialogues") {

            authenticate("basic-auth-session") {

                get("/by-id") {
                    val dialogueId = UUID.fromString(call.parameters["dialogueId"])
                    call.respond(HttpStatusCode.OK, dialogueService.findById(dialogueId))
                }

                delete("/delete") {
                    val dialogueId = UUID.fromString(call.parameters["dialogueId"])
                    dialogueService.delete(dialogueId)
                    call.respond(HttpStatusCode.OK)
                }
            }
        }
    }
}