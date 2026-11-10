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
    EXPLICIT("Explicit", "direct and respectful adult intimacy")
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
