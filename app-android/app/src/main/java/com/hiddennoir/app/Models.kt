package com.hiddennoir.app

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

enum class Relationship(val apiValue: String, val label: String) {
    NEW_MATCH("new_match", "New match"),
    DATING("dating", "Dating"),
    HOOKUP("hookup", "Hookup"),
    PARTNER("partner", "Partner")
}

enum class Tone(val apiValue: String, val label: String) {
    PLAYFUL("playful", "Playful"),
    CONFIDENT("confident", "Confident"),
    ROMANTIC("romantic", "Romantic"),
    DOMINANT("dominant", "Dominant"),
    SUBMISSIVE("submissive", "Submissive")
}

enum class Intensity(val apiValue: String, val label: String) {
    FLIRTY("flirty", "Flirty"),
    SUGGESTIVE("suggestive", "Suggestive"),
    EXPLICIT("explicit", "Explicit")
}

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
data class ApiError(
    val error: String,
    val message: String? = null
)

@Serializable
data class OllamaMessage(val role: String, val content: String)

@Serializable
data class OllamaOptions(
    val temperature: Double = 0.95,
    @SerialName("top_p") val topP: Double = 0.92
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
data class OllamaChatResponse(val model: String, val message: OllamaMessage)

@Serializable
data class ModelReplyEnvelope(val replies: List<String>)
