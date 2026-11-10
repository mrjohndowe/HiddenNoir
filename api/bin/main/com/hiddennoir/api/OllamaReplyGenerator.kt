package com.hiddennoir.api

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

class OllamaUnavailableException(
    message: String,
    cause: Throwable? = null
) : RuntimeException(message, cause)

/**
 * Makes a single, non-streaming Ollama chat request for each generation.
 *
 * The standard JDK client avoids Ktor engine-specific networking behaviour and
 * gives this service explicit connection and whole-request time limits.
 */
class OllamaReplyGenerator(
    private val baseUrl: String,
    val model: String
) : AutoCloseable {

    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
    }

    private val client = HttpClient.newBuilder()
        .connectTimeout(CONNECT_TIMEOUT)
        .build()

    suspend fun generate(
        request: GenerationRequest,
        attempt: Int = 0
    ): List<String> {
        val replyCount = request.replyCount.coerceIn(MIN_REPLY_COUNT, MAX_REPLY_COUNT)
        val relationshipGuidance = relationshipGuidance(request.relationship)
        val toneGuidance = toneGuidance(request.tone)
        val intensityGuidance = intensityGuidance(request.intensity)
        val systemPrompt = """
            You are HiddenNoir, an adult dating reply assistant.

            Return JSON only in exactly this shape:
            {"replies":["reply 1","reply 2","reply 3","reply 4"]}

            Rules:
            - Produce exactly $replyCount distinct candidate replies.
            - Relationship selection is ${request.relationship}: $relationshipGuidance
            - Tone selection is ${request.tone}: $toneGuidance
            - Intensity selection is ${request.intensity}: $intensityGuidance
            - These three selections are requirements, not background information.
              A reply for another selection must sound noticeably different.
            - Keep each reply concise enough to send as a text message.
            - Do not mention that you are an AI.
            - Sexual content must only concern consenting adults.
            - Never generate sexual content involving minors, uncertain age,
              coercion, exploitation, unconscious people, or non-consent.
            - Do not output analysis, markdown, labels, or explanations.
        """.trimIndent()
        val userPrompt = """
            Conversation:
            ---
            ${request.conversation}
            ---

            Suggest the next messages the user could send.
        """.trimIndent()

        val body = json.encodeToString(
            OllamaChatRequest(
                model = model,
                messages = listOf(
                    OllamaMessage(role = "system", content = systemPrompt),
                    OllamaMessage(role = "user", content = userPrompt)
                ),
                stream = false,
                options = OllamaOptions(temperature = 0.95, topP = 0.92)
            )
        )
        val endpoint = "${baseUrl.trimEnd('/')}/api/chat"
        val httpRequest = HttpRequest.newBuilder()
            .uri(toUri(endpoint))
            .timeout(REQUEST_TIMEOUT)
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(body))
            .build()

        val response = try {
            withContext(Dispatchers.IO) {
                client.send(httpRequest, HttpResponse.BodyHandlers.ofString())
            }
        } catch (error: Exception) {
            throw OllamaUnavailableException(
                "Could not reach Ollama at $baseUrl using model $model within ${REQUEST_TIMEOUT.seconds} seconds.",
                error
            )
        }

        if (response.statusCode() !in 200..299) {
            throw OllamaUnavailableException(
                "Ollama returned HTTP ${response.statusCode()} for model $model.${response.body().errorDetail()}"
            )
        }

        val replies = parseReplies(extractMessageContent(response.body()))

        val usableReplies = replies
            .map { reply -> reply.replace("—", ",").trim() }
            .filter(String::isNotBlank)
            .distinct()
            .take(replyCount)

        if (usableReplies.size != replyCount || usableReplies.any { it.length < MIN_REPLY_LENGTH }) {
            if (attempt < MAX_GENERATION_RETRIES) {
                return generate(request, attempt + 1)
            }

            return normaliseReplies(fallbackReplies(request, replyCount))
        }

        return normaliseReplies(usableReplies)
    }

    override fun close() = Unit

    private fun toUri(endpoint: String): URI =
        try {
            URI.create(endpoint)
        } catch (error: IllegalArgumentException) {
            throw OllamaUnavailableException("OLLAMA_BASE_URL is not a valid URL: $baseUrl", error)
        }

    private fun String.errorDetail(): String {
        val compact = trim().replace(Regex("\\s+"), " ")
        return if (compact.isBlank()) "" else " Response: ${compact.take(MAX_ERROR_BODY_LENGTH)}"
    }

    private fun parseReplies(content: String): List<String> {
        val jsonContent = extractReplyJson(content)

        return try {
            json.decodeFromString<ModelReplyEnvelope>(jsonContent).replies
        } catch (envelopeError: SerializationException) {
            try {
                json.decodeFromString<List<String>>(jsonContent)
            } catch (arrayError: SerializationException) {
                val fallback = fallbackReplies(content)
                if (fallback.isNotEmpty()) return fallback

                throw OllamaUnavailableException(
                    "Ollama returned invalid reply JSON. Try generating again.",
                    arrayError
                )
            }
        }
    }

    private fun fallbackReplies(content: String): List<String> {
        val quotedReplies = QUOTED_REPLY_PATTERN
            .findAll(content)
            .map { it.groupValues[1].trim() }
            .filter { it.isNotBlank() && it != "replies" }
            .toList()

        if (quotedReplies.isNotEmpty()) return quotedReplies

        return content
            .lineSequence()
            .map { it.trim().removePrefix("-").removePrefix("*").trim() }
            .filter { it.isNotBlank() && !it.startsWith("```") }
            .toList()
    }

    private fun fallbackReplies(
        request: GenerationRequest,
        replyCount: Int
    ): List<String> {
        val relationship = when (request.relationship.lowercase()) {
            "new_match" -> "We are still getting to know each other"
            "dating" -> "I like where we are headed"
            "hookup" -> "The chemistry between us is obvious"
            "partner" -> "You know exactly how to get to me"
            else -> "I like the connection we have"
        }
        val tone = when (request.tone.lowercase()) {
            "playful" -> "You are making this hard to play cool"
            "confident" -> "I know what I want"
            "romantic" -> "I have been thinking about you all day"
            "dominant" -> "Be ready when I say the word"
            "submissive" -> "Tell me what you have in mind"
            else -> "You have my attention"
        }
        val intensity = when (request.intensity.lowercase()) {
            "flirty" -> "Want to turn that late night into a real plan?"
            "suggestive" -> "I have a few tempting ideas for when we finally get some time alone."
            "explicit" -> "I want some private, adult time with you tonight. Tell me what you want first."
            else -> "When are we making this happen?"
        }

        return listOf(
            "$relationship, and $tone.",
            "$tone. $intensity",
            "$relationship. $intensity",
            "$tone, so do not keep me waiting too long."
        ).take(replyCount)
    }

    private fun relationshipGuidance(relationship: String): String =
        when (relationship.lowercase()) {
            "new_match" -> "Keep it curious and low-pressure. Do not imply an established history or deep intimacy."
            "dating" -> "Use comfortable growing attraction, as two people who have already gone out together."
            "hookup" -> "Keep it casual, direct, chemistry-led, and avoid promises of a relationship."
            "partner" -> "Use the warmth and familiarity of an established intimate relationship."
            else -> "Use the relationship label naturally without inventing a different relationship."
        }

    private fun toneGuidance(tone: String): String =
        when (tone.lowercase()) {
            "playful" -> "Use light teasing and a fun, mischievous rhythm."
            "confident" -> "Be decisive and self-assured, without sounding hostile."
            "romantic" -> "Be affectionate, warm, and emotionally attentive."
            "dominant" -> "Use consensual, assertive invitation and clear confidence."
            "submissive" -> "Use willing, receptive language while retaining personal agency and consent."
            else -> "Use a natural conversational tone."
        }

    private fun intensityGuidance(intensity: String): String =
        when (intensity.lowercase()) {
            "flirty" -> "Keep it light and charming. Do not use sexual details or strong innuendo."
            "suggestive" -> "Use mature innuendo and anticipation, but do not use graphic sexual details."
            "explicit" -> "The user confirmed every person is an adult and consenting. Write direct, sexually explicit but respectful adult messages."
            else -> "Keep it natural and suitable for an adult dating conversation."
        }

    private fun normaliseReplies(replies: List<String>): List<String> =
        replies.map { reply ->
            reply
                .replace(Regex("[\\u2013\\u2014]"), ",")
                .replace(Regex(",\\s*,"), ",")
                .trim()
        }

    private fun extractReplyJson(content: String): String {
        val trimmed = content
            .trim()
            .removePrefix("```json")
            .removePrefix("```")
            .removeSuffix("```")
            .trim()

        val objectStart = trimmed.indexOf('{')
        val objectEnd = trimmed.lastIndexOf('}')
        if (objectStart >= 0 && objectEnd > objectStart) {
            return trimmed.substring(objectStart, objectEnd + 1)
        }

        val arrayStart = trimmed.indexOf('[')
        val arrayEnd = trimmed.lastIndexOf(']')
        if (arrayStart >= 0 && arrayEnd > arrayStart) {
            return trimmed.substring(arrayStart, arrayEnd + 1)
        }

        return trimmed
    }

    private fun extractMessageContent(responseBody: String): String {
        val root = try {
            json.parseToJsonElement(responseBody) as? JsonObject
        } catch (_: SerializationException) {
            null
        }

        val content = root
            ?.get("message")
            ?.let { it as? JsonObject }
            ?.get("content")
            ?.jsonPrimitive
            ?.contentOrNull

        if (content != null) return content

        val escapedContent = MESSAGE_CONTENT_PATTERN
            .find(responseBody)
            ?.groupValues
            ?.getOrNull(1)

        if (escapedContent != null) {
            return try {
                json.decodeFromString<String>("\"$escapedContent\"")
            } catch (_: SerializationException) {
                escapedContent.replace("\\\"", "\"").replace("\\n", "\n")
            }
        }

        throw OllamaUnavailableException("Ollama response did not contain message content.")
    }

    private companion object {
        val CONNECT_TIMEOUT: Duration = Duration.ofSeconds(10)
        val REQUEST_TIMEOUT: Duration = Duration.ofSeconds(90)
        const val MIN_REPLY_COUNT = 3
        const val MAX_REPLY_COUNT = 5
        const val MIN_REPLY_LENGTH = 8
        const val MAX_GENERATION_RETRIES = 2
        const val MAX_ERROR_BODY_LENGTH = 300
        val MESSAGE_CONTENT_PATTERN = Regex("\\\"content\\\"\\s*:\\s*\\\"((?:\\\\\\.|[^\\\"\\\\])*)\\\"")
        val QUOTED_REPLY_PATTERN = Regex("\\\"((?:\\\\\\.|[^\\\"])*)\\\"")
    }
}
