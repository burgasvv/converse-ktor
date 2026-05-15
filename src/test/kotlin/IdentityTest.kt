package org.burgas

import io.ktor.client.call.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.cookies.HttpCookies
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.testing.*
import kotlinx.coroutines.Dispatchers
import kotlinx.serialization.json.Json
import org.burgas.dao.IdentityEntity
import org.burgas.database.Authority
import org.burgas.database.DatabaseConnection
import org.burgas.database.IdentityTable
import org.burgas.dto.CsrfToken
import org.burgas.dto.IdentityRequest
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import kotlin.test.Test
import kotlin.test.assertEquals

class IdentityTest {

    @Test
    fun `identity router test`() = testApplication {
        configure()
        val httpClient = createClient {
            install(ContentNegotiation) {
                json(
                    Json {
                        this.explicitNulls = true
                        this.ignoreUnknownKeys = true
                        this.isLenient = true
                        this.prettyPrint = true
                    }
                )
            }
            install(HttpCookies)
        }

        val csrfToken = httpClient.get("/api/v1/security/csrf-token") {
            header(HttpHeaders.Host, "localhost:9000")
            header(HttpHeaders.Accept, ContentType.Application.Json)
        }.body<CsrfToken>()

        httpClient.post("/api/v1/identities/create") {
            header(HttpHeaders.Host, "localhost:9000")
            header(HttpHeaders.Accept, ContentType.Application.Json)
            header(HttpHeaders.ContentType, ContentType.Application.Json)
            header(HttpHeaders.Origin, "http://localhost:9000")
            header("X-CSRF-Token", csrfToken.value)
            val identityRequest = IdentityRequest(
                authority = Authority.ADMIN, username = "admin", password = "admin", email = "admin@gmail.com",
                phone = "+79456351245", status = true, firstname = "Admin", lastname = "Admin", patronymic = "Admin"
            )
            this.setBody(identityRequest)
        }
            .apply {
                assertEquals(HttpStatusCode.OK, this.status)
            }

        val identityEntity = newSuspendedTransaction(
            db = DatabaseConnection.postgres, context = Dispatchers.Default, readOnly = true
        ) {
            IdentityEntity.find { IdentityTable.email eq "admin@gmail.com" }.single()
        }

        httpClient.get("/api/v1/security/login") {
            header(HttpHeaders.Host, "localhost:9000")
            header(HttpHeaders.Accept, ContentType.Text.Plain)
            basicAuth("admin@gmail.com", "admin")
        }
            .apply {
                assertEquals(HttpStatusCode.OK, this.status)
            }

        httpClient.post("/api/v1/identities/update") {
            header(HttpHeaders.Host, "localhost:9000")
            header(HttpHeaders.Accept, ContentType.Application.Json)
            header(HttpHeaders.ContentType, ContentType.Application.Json)
            header(HttpHeaders.Origin, "http://localhost:9000")
            header("X-CSRF-Token", csrfToken.value)
            val identityRequest = IdentityRequest(
                id = identityEntity.id.value, username = "admin test"
            )
            this.setBody(identityRequest)
        }
            .apply {
                assertEquals(HttpStatusCode.Found, this.status)
            }

        httpClient.post("/api/v1/identities/delete") {
            header(HttpHeaders.Host, "localhost:9000")
            header(HttpHeaders.Accept, ContentType.Application.Json)
            header(HttpHeaders.Origin, "http://localhost:9000")
            header("X-CSRF-Token", csrfToken.value)
            parameter("identityId", identityEntity.id.value)
        }
            .apply {
                assertEquals(HttpStatusCode.Found, this.status)
            }
    }
}