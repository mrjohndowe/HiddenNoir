package com.hiddennoir.app

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
    val replies: List<ReplyCandidate>
)

@Serializable
data class ReplyCandidate(
    val id: String,
    val text: String,
    val tone: String,
    val intensity: String
)
