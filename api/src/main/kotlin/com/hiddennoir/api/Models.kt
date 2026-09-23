package com.hiddennoir.api

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class HealthResponse(
    val status: String,
    val model: String
)

@Serializable
data class ApiError(
    val error: String,
    val message: String? = null
)

@Serializable
data class AnonymousSessionResponse(
    val accessToken: String,
    val expiresInSeconds: Long
)

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
    val replies: List<ReplyCandidate>,
    val model: String
)

@Serializable
data class ReplyCandidate(
    val id: String,
    val text: String
)

@Serializable
data class OllamaMessage(
    val role: String,
    val content: String
)

@Serializable
data class OllamaChatRequest(
    val model: String,
    val messages: List<OllamaMessage>,
    val stream: Boolean = false,
    val think: Boolean = false,
    val format: String = "json",
    val options: OllamaOptions = OllamaOptions()
)

@Serializable
data class OllamaOptions(
    val temperature: Double = 0.8,
    @SerialName("top_p")
    val topP: Double = 0.9
)

@Serializable
data class OllamaChatResponse(
    val model: String,
    val message: OllamaMessage,
    val done: Boolean = true
)

@Serializable
data class ModelReplyEnvelope(
    val replies: List<String>
)
