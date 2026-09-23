package com.hiddennoir.app

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.contentType
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import java.util.UUID

/** Direct private Ollama client. The APK does not require the HiddenNoir API server. */
class HiddenNoirApi(
    private val ollamaBaseUrl: String = BuildConfig.OLLAMA_BASE_URL,
    private val model: String = BuildConfig.OLLAMA_MODEL
) {
    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false }
    private val client = HttpClient(OkHttp) {
        expectSuccess = true
        install(HttpTimeout) { connectTimeoutMillis = 10_000; requestTimeoutMillis = 120_000; socketTimeoutMillis = 120_000 }
        install(ContentNegotiation) { json(json) }
    }

    suspend fun generate(request: GenerationRequest): GenerationResponse {
        validate(request)
        val count = request.replyCount.coerceIn(3, 5)
        val response: OllamaChatResponse = client.post("${ollamaBaseUrl.trimEnd('/')}/api/chat") {
            contentType(ContentType.Application.Json)
            setBody(OllamaChatRequest(
                model = model,
                messages = listOf(
                    OllamaMessage("system", prompt(request, count)),
                    OllamaMessage("user", "Conversation:\n---\n${request.conversation}\n---\nSuggest the next messages the user could send.")
                ),
                options = OllamaOptions(temperature = 0.95, topP = 0.92)
            ))
        }.body()

        val replies = decodeReplies(response.message.content, count)
        if (replies.size != count) throw IllegalStateException("Ollama did not return $count usable reply options. Please try again.")
        return GenerationResponse(UUID.randomUUID().toString(), replies.map { ReplyCandidate(UUID.randomUUID().toString(), it) }, response.model)
    }

    private fun prompt(request: GenerationRequest, count: Int) = """
        You are HiddenNoir, an adult dating reply assistant. Return JSON only:
        {"replies":["reply 1","reply 2","reply 3","reply 4"]}
        Produce exactly $count distinct, concise text messages. Do not use em dashes.
        Relationship ${request.relationship}: ${relationship(request.relationship)}.
        Tone ${request.tone}: ${tone(request.tone)}.
        Intensity ${request.intensity}: ${intensity(request.intensity)}.
        These settings are requirements and must clearly affect the wording. Never mention AI.
        Sexual content must involve consenting adults only. Never generate content involving minors,
        uncertain age, coercion, exploitation, unconscious people, or non-consent.
    """.trimIndent()

    private fun decodeReplies(content: String, count: Int): List<String> {
        val clean = content.trim().removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
        val first = clean.indexOf('{'); val last = clean.lastIndexOf('}')
        if (first < 0 || last <= first) return emptyList()
        return try {
            json.decodeFromString<ModelReplyEnvelope>(clean.substring(first, last + 1)).replies
                .map { it.replace(Regex("[\\u2013\\u2014]"), ",").trim() }
                .filter { it.length >= 8 }.distinct().take(count)
        } catch (_: SerializationException) { emptyList() }
    }

    private fun validate(request: GenerationRequest) {
        if (request.intensity.equals("explicit", true) && !request.adultAttestation) throw IllegalArgumentException("Confirm everyone represented is 18 or older before using Explicit mode.")
        val minorCue = Regex("(?i)\\b(?:age\\s*)?(?:1[0-7])\\s*(?:years?\\s*old|yo|y/o)\\b|\\bunderage\\b|\\ba minor\\b")
        if (request.intensity.equals("explicit", true) && minorCue.containsMatchIn(request.conversation)) throw IllegalArgumentException("Explicit mode cannot be used when the conversation indicates someone may be under 18.")
    }

    private fun relationship(value: String) = when (value.lowercase()) { "new_match" -> "curious and low-pressure, without invented history"; "dating" -> "comfortable growing attraction"; "hookup" -> "casual, direct, and chemistry-led"; "partner" -> "warm, familiar, established intimacy"; else -> "natural adult connection" }
    private fun tone(value: String) = when (value.lowercase()) { "playful" -> "light teasing and mischievous"; "confident" -> "decisive and self-assured"; "romantic" -> "affectionate and emotionally attentive"; "dominant" -> "consensually assertive"; "submissive" -> "willing and receptive while retaining agency"; else -> "natural" }
    private fun intensity(value: String) = when (value.lowercase()) { "flirty" -> "light and charming, no sexual details"; "suggestive" -> "mature innuendo and anticipation, no graphic detail"; "explicit" -> "direct, respectful adult intimacy; all people are confirmed consenting adults"; else -> "natural adult dating" }
}
