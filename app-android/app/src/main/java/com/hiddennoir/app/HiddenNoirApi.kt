package com.hiddennoir.app

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.*
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

class HiddenNoirApi(
    private val baseUrl: String = BuildConfig.API_BASE_URL
) {
    private val jsonConfig = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
    }

    private val client = HttpClient(OkHttp) {
        expectSuccess = true

        install(HttpTimeout) {
            connectTimeoutMillis = 10_000
            requestTimeoutMillis = 105_000
            socketTimeoutMillis = 105_000
        }

        install(ContentNegotiation) {
            json(jsonConfig)
        }
    }

    suspend fun createAnonymousSession(): AnonymousSessionResponse =
        client.post("$baseUrl/v1/sessions/anonymous") {
            contentType(ContentType.Application.Json)
        }.body()

    suspend fun generate(
        accessToken: String,
        request: GenerationRequest
    ): GenerationResponse =
        client.post("$baseUrl/v1/generations") {
            header("Authorization", "Bearer $accessToken")
            contentType(ContentType.Application.Json)
            setBody(request)
        }.body()
}
