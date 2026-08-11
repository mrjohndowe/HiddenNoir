package com.hiddennoir.api

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
import kotlinx.serialization.json.Json
import org.slf4j.event.Level
import java.util.UUID

private const val SESSION_TTL_SECONDS = 86400L

fun main() {
    migrateDatabaseFromEnvironment()

    val redisHost = System.getenv("REDIS_HOST") ?: "localhost"
    val redisPort = System.getenv("REDIS_PORT")?.toIntOrNull() ?: 6379
    val store = RedisSessionStore(redisHost, redisPort)

    Runtime.getRuntime().addShutdownHook(Thread { store.close() })

    val port = System.getenv("API_PORT")?.toIntOrNull() ?: 8080
    embeddedServer(Netty, port = port, host = "0.0.0.0") {
        module(store)
    }.start(wait = true)
}

fun Application.module(sessionStore: SessionStore = InMemorySessionStore()) {
    install(ContentNegotiation) {
        json(Json {
            ignoreUnknownKeys = false
            explicitNulls = false
        })
    }

    install(CallLogging) {
        level = Level.INFO
        // Never log request/response bodies for conversation routes.
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

        post("/v1/sessions/anonymous") {
            val token = sessionStore.createSession(SESSION_TTL_SECONDS)
            call.respond(
                AnonymousSessionResponse(
                    accessToken = token,
                    expiresInSeconds = SESSION_TTL_SECONDS
                )
            )
        }

        post("/v1/generations") {
            val token = call.bearerToken()
            if (token == null || !sessionStore.isValid(token)) {
                call.respond(HttpStatusCode.Unauthorized, ApiError("invalid_session"))
                return@post
            }

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

            // Development stub only.
            // M2/M3: quota -> input safety -> model -> output safety.
            val replies = List(request.replyCount.coerceIn(3, 5)) { index ->
                ReplyCandidate(
                    id = UUID.randomUUID().toString(),
                    text = "HiddenNoir development reply ${index + 1}",
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

private fun ApplicationCall.bearerToken(): String? {
    val header = request.headers["Authorization"] ?: return null
    if (!header.startsWith("Bearer ", ignoreCase = true)) return null
    return header.substringAfter("Bearer ").trim().takeIf { it.isNotEmpty() }
}
