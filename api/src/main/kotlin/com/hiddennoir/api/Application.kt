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
import kotlin.system.measureTimeMillis

private const val SESSION_TTL_SECONDS = 7L * 24L * 60L * 60L

fun main() {
    val databasePath =
        System.getenv("HIDDENNOIR_DB_PATH")
            ?: "data/hiddennoir.db"

    // This development server and Ollama run on the same Windows machine.
    val ollamaBaseUrl = "http://127.0.0.1:11434"
    val ollamaModel =
        System.getenv("OLLAMA_MODEL")
            ?.takeIf { it.isNotBlank() }
            ?: "jimscard/adult-film-screenwriter-nsfw:latest"

    val dailyLimit =
        System.getenv("DEV_DAILY_LIMIT")
            ?.toIntOrNull()
            ?: 100

    val database = HiddenNoirDatabase(databasePath)

    val generator =
        OllamaReplyGenerator(
            baseUrl = ollamaBaseUrl,
            model = ollamaModel
        )

    Runtime.getRuntime().addShutdownHook(
        Thread {
            generator.close()
        }
    )

    val port =
        System.getenv("API_PORT")
            ?.toIntOrNull()
            ?: 8081

    embeddedServer(
        factory = Netty,
        port = port,
        host = "0.0.0.0"
    ) {
        module(
            database = database,
            generator = generator,
            dailyLimit = dailyLimit
        )
    }.start(wait = true)
}

fun Application.module(
    database: HiddenNoirDatabase,
    generator: OllamaReplyGenerator,
    dailyLimit: Int = 100
) {
    val sessions = SessionService(database)

    install(ContentNegotiation) {
        json(
            Json {
                ignoreUnknownKeys = false
                explicitNulls = false
            }
        )
    }

    install(CallLogging) {
        level = Level.INFO
        // Never log request or response bodies here.
    }

    install(StatusPages) {
        exception<OllamaUnavailableException> { call, cause ->
            call.application.environment.log.warn(
                "Ollama request failed: ${cause.message}"
            )

            call.respond(
                HttpStatusCode.ServiceUnavailable,
                ApiError(
                    error = "ollama_unavailable",
                    message = cause.message
                )
            )
        }

        exception<Throwable> { call, cause ->
            call.application.environment.log.error(
                "Unhandled request failure",
                cause
            )

            call.respond(
                HttpStatusCode.InternalServerError,
                ApiError("internal_error")
            )
        }
    }

    routing {
        get("/health") {
            call.respond(
                HealthResponse(
                    status = "ok",
                    model = generator.model
                )
            )
        }

        post("/v1/sessions/anonymous") {
            val token =
                sessions.createSession(
                    ttlSeconds = SESSION_TTL_SECONDS
                )

            call.respond(
                AnonymousSessionResponse(
                    accessToken = token,
                    expiresInSeconds = SESSION_TTL_SECONDS
                )
            )
        }

        post("/v1/generations") {
            val token = call.bearerToken()


            if (token == null || !sessions.isValid(token)) {
                call.respond(
                    HttpStatusCode.Unauthorized,
                    ApiError("invalid_session")
                )
                return@post
            }

            if (database.generationCountToday(token) >= dailyLimit) {
                call.respond(
                    HttpStatusCode.TooManyRequests,
                    ApiError(
                        error = "daily_limit_reached",
                        message = "Development daily limit reached."
                    )
                )
                return@post
            }

            val request = call.receive<GenerationRequest>()
            val policy = BasicPolicy.evaluate(request)

            if (!policy.allowed) {
                database.recordGeneration(
                    token = token,
                    mode = request.intensity,
                    inputCharacterCount = request.conversation.length,
                    returnedReplyCount = 0,
                    policyResult = policy.reason ?: "blocked",
                    modelVersion = generator.model,
                    latencyMs = 0
                )

                call.respond(
                    HttpStatusCode.UnprocessableEntity,
                    ApiError(
                        error = "generation_not_allowed",
                        message = policy.reason
                    )
                )
                return@post
            }

            var replies: List<String> = emptyList()

            call.application.environment.log.info("Calling Ollama generator...")

            val latency =
                measureTimeMillis {
                    replies = generator.generate(request)
                }

            call.application.environment.log.info(
                "Generator returned ${replies.size} replies."
            )

            if (replies.isEmpty()) {
                throw OllamaUnavailableException(
                    "Ollama returned no usable replies."
                )
            }

            database.recordGeneration(
                token = token,
                mode = request.intensity,
                inputCharacterCount = request.conversation.length,
                returnedReplyCount = replies.size,
                policyResult = "allowed",
                modelVersion = generator.model,
                latencyMs = latency
            )

            call.respond(
                GenerationResponse(
                    generationId = UUID.randomUUID().toString(),
                    replies = replies.map { text ->
                        ReplyCandidate(
                            id = UUID.randomUUID().toString(),
                            text = text
                        )
                    },
                    model = generator.model
                )
            )
        }
    }
}


private fun ApplicationCall.bearerToken(): String? {
    val header =
        request.headers["Authorization"]
            ?: return null

    if (!header.startsWith("Bearer ", ignoreCase = true)) {
        return null
    }

    return header
        .substringAfter("Bearer ")
        .trim()
        .takeIf { it.isNotEmpty() }
}
