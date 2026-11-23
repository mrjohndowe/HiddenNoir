package com.hiddennoir.app

import kotlinx.serialization.Serializable

enum class Relationship(val label: String, val direction: String) {
    NEW_MATCH("New match", "curious and low-pressure, without invented history"),
    DATING("Dating", "comfortable growing attraction"),
    HOOKUP("Hookup", "casual, direct, and chemistry-led"),
    PARTNER("Partner", "warm, familiar, and established")
}

enum class Tone(val label: String, val direction: String) {
    PLAYFUL("Playful", "light teasing"),
    CONFIDENT("Confident", "decisive and self-assured"),
    ROMANTIC("Romantic", "affectionate and emotionally attentive"),
    DOMINANT("Dominant", "consensually assertive"),
    SUBMISSIVE("Submissive", "receptive while retaining agency")
}

enum class Intensity(val label: String, val direction: String) {
    FLIRTY("Flirty", "light and charming, with no sexual details"),
    SUGGESTIVE("Suggestive", "mature innuendo without graphic detail"),
    EXPLICIT("Explicit", "graphic, vulgar dirty talk when the conversation clearly invites it")
}

@Serializable
data class Draft(
    val conversation: String = "",
    val relationship: Relationship = Relationship.NEW_MATCH,
    val tone: Tone = Tone.PLAYFUL,
    val intensity: Intensity = Intensity.FLIRTY,
    val replies: List<String> = emptyList()
)

@Serializable
data class ModelReplyEnvelope(val replies: List<String>)

@Serializable
data class TextReview(
    val message: String = "",
    val context: String = "",
    val feedback: List<String> = emptyList()
)

@Serializable
internal data class ReviewResponse(
    val rewrite: String,
    val assessment: String,
    val issue: String,
    val reason: String
)
