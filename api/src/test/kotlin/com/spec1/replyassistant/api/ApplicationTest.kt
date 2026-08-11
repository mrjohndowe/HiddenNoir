package com.spec1.replyassistant.api

import io.ktor.client.request.*
import io.ktor.client.statement.bodyAsText
import io.ktor.http.*
import io.ktor.server.testing.testApplication
import kotlin.test.*

class ApplicationTest {
    @Test
    fun healthEndpointReturnsOk() = testApplication {
        application { module() }
        val response = client.get("/health")
        assertEquals(HttpStatusCode.OK, response.status)
        assertTrue(response.bodyAsText().contains("\"status\":\"ok\""))
    }

    @Test
    fun explicitModeRequiresAdultAttestation() = testApplication {
        application { module() }
        val payload = "{\"conversation\":\"test\",\"relationship\":\"new_match\",\"tone\":\"playful\",\"intensity\":\"explicit\",\"adultAttestation\":false,\"replyCount\":4}"
        val response = client.post("/v1/generations") {
            contentType(ContentType.Application.Json)
            setBody(payload)
        }
        assertEquals(HttpStatusCode.UnprocessableEntity, response.status)
    }
}
