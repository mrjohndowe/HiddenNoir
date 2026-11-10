package com.hiddennoir.api

data class PolicyResult(
    val allowed: Boolean,
    val reason: String? = null
)

object BasicPolicy {
    private val minorPatterns = listOf(
        Regex("""(?i)\bage\s*(?:1[0-7])\b"""),
        Regex("""(?i)\b(?:1[0-7])\s*(?:years?\s*old|yo|y/o)\b"""),
        Regex("""(?i)\bunderage\b"""),
        Regex("""(?i)\ba minor\b""")
    )

    private val nonConsentPatterns = listOf(
        Regex("""(?i)\bwithout (?:their|her|his) consent\b"""),
        Regex("""(?i)\bnon[- ]?consensual\b"""),
        Regex("""(?i)\bunconscious\b"""),
        Regex("""(?i)\bdrugged\b"""),
        Regex("""(?i)\bforce (?:them|her|him)\b""")
    )

    fun evaluate(request: GenerationRequest): PolicyResult {
        if (request.conversation.length > 12_000) {
            return PolicyResult(false, "conversation_too_long")
        }

        if (request.intensity == "explicit" && !request.adultAttestation) {
            return PolicyResult(false, "adult_attestation_required")
        }

        if (request.intensity == "explicit") {
            if (minorPatterns.any { it.containsMatchIn(request.conversation) }) {
                return PolicyResult(false, "minor_or_uncertain_age")
            }

            if (nonConsentPatterns.any { it.containsMatchIn(request.conversation) }) {
                return PolicyResult(false, "non_consent")
            }
        }

        return PolicyResult(true)
    }
}
