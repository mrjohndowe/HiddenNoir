package com.hiddennoir.api

import io.ktor.client.request.*
import io.ktor.client.statement.bodyAsText
import io.ktor.http.*
import io.ktor.server.testing.testApplication
import kotlin.test.*

class ApplicationTest {

    @Test
    fun healthEndpointReturnsOk() = testApplication {
        application { module(InMemorySessionStore()) }
        val response = client.get("/health")
        assertEquals(HttpStatusCode.OK, response.status)
        assertTrue(response.bodyAsText().contains("\"status\":\"ok\""))
    }

    @Test
    fun generationRequiresSession() = testApplication {
        application { module(InMemorySessionStore()) }
        val response = client.post("/v1/generations") {
            contentType(ContentType.Application.Json)
            setBody(flirtyPayload())
        }
        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }

    @Test
    fun explicitModeRequiresAdultAttestation() = testApplication {
        val store = InMemorySessionStore()
        application { module(store) }

        val token = store.createSession(3600)

        val response = client.post("/v1/generations") {
            header("Authorization", "Bearer $token")
            contentType(ContentType.Application.Json)
            setBody(explicitPayload(false))
        }

        assertEquals(HttpStatusCode.UnprocessableEntity, response.status)
    }

    private fun flirtyPayload() =
        "{\"conversation\":\"hello\",\"relationship\":\"new_match\",\"tone\":\"playful\",\"intensity\":\"flirty\",\"adultAttestation\":false,\"replyCount\":4}"

    private fun explicitPayload(attested: Boolean) =
        "{\"conversation\":\"hello\",\"relationship\":\"dating\",\"tone\":\"confident\",\"intensity\":\"explicit\",\"adultAttestation\":$attested,\"replyCount\":4}"
}
