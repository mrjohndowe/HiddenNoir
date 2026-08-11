package com.hiddennoir.api

import kotlinx.serialization.Serializable

@Serializable data class HealthResponse(val status: String)
@Serializable data class ApiError(val error: String)

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
