package com.spec1.replyassistant.api

import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.*
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.plugins.calllogging.CallLogging
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable
import org.slf4j.event.Level
import java.util.UUID

fun main() {
    val port = System.getenv("API_PORT")?.toIntOrNull() ?: 8080
    embeddedServer(Netty, port = port, host = "0.0.0.0") { module() }.start(wait = true)
}

fun Application.module() {
    install(ContentNegotiation) { json() }
    install(CallLogging) {
        level = Level.INFO
        // Request/response bodies are intentionally not logged.
    }
    install(StatusPages) {
        exception<Throwable> { call, cause ->
            environment.log.error("Unhandled request failure", cause)
            call.respond(HttpStatusCode.InternalServerError, ApiError("internal_error"))
        }
    }

    routing {
        get("/health") {
            call.respond(HealthResponse("ok"))
        }

        post("/v1/generations") {
            val request = call.receive<GenerationRequest>()

            if (request.conversation.isBlank()) {
                call.respond(HttpStatusCode.BadRequest, ApiError("conversation_required"))
                return@post
            }

            if (request.intensity == "explicit" && !request.adultAttestation) {
                call.respond(
                    HttpStatusCode.UnprocessableEntity,
                    ApiError("adult_attestation_required")
                )
                return@post
            }

            // Development-only placeholder.
            // Replace in M2/M3 with entitlement -> safety -> LLM -> output safety.
            val replies = List(request.replyCount.coerceIn(3, 5)) { index ->
                ReplyCandidate(
                    id = UUID.randomUUID().toString(),
                    text = "Development placeholder reply ${index + 1}",
                    tone = request.tone,
                    intensity = request.intensity
                )
            }

            call.respond(
                GenerationResponse(
                    generationId = UUID.randomUUID().toString(),
                    replies = replies
                )
            )
        }
    }
}

@Serializable data class HealthResponse(val status: String)
@Serializable data class ApiError(val error: String)

@Serializable
data class GenerationRequest(
    val conversation: String,
    val relationship: String,
    val tone: String,
    val intensity: String,
    val adultAttestation: Boolean,
    val replyCount: Int = 4
)

@Serializable
data class GenerationResponse(
    val generationId: String,
    val replies: List<ReplyCandidate>
)

@Serializable
data class ReplyCandidate(
    val id: String,
    val text: String,
    val tone: String,
    val intensity: String
)
