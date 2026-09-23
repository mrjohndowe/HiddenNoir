package com.hiddennoir.api

import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DatabaseAndPolicyTest {

    @Test
    fun sqliteSessionRoundTripWorks() {
        val tempFile =
            Files.createTempFile("hiddennoir-test-", ".db")

        try {
            val database =
                HiddenNoirDatabase(
                    tempFile.toString()
                )

            database.createSession(
                token = "test-token",
                ttlSeconds = 3600
            )

            assertTrue(
                database.isSessionValid("test-token")
            )

            assertFalse(
                database.isSessionValid("wrong-token")
            )
        } finally {
            Files.deleteIfExists(tempFile)
        }
    }

    @Test
    fun explicitModeRequiresAdultAttestation() {
        val result =
            BasicPolicy.evaluate(
                GenerationRequest(
                    conversation = "A normal adult conversation.",
                    relationship = "dating",
                    tone = "playful",
                    intensity = "explicit",
                    adultAttestation = false
                )
            )

        assertFalse(result.allowed)
    }

    @Test
    fun explicitMinorCueIsBlocked() {
        val result =
            BasicPolicy.evaluate(
                GenerationRequest(
                    conversation = "They said they are 17 years old.",
                    relationship = "dating",
                    tone = "playful",
                    intensity = "explicit",
                    adultAttestation = true
                )
            )

        assertFalse(result.allowed)
    }

    @Test
    fun explicitScheduleDurationIsAllowed() {
        val result =
            BasicPolicy.evaluate(
                GenerationRequest(
                    conversation = "I was supposed to have 14 days off from work.",
                    relationship = "partner",
                    tone = "romantic",
                    intensity = "explicit",
                    adultAttestation = true
                )
            )

        assertTrue(result.allowed)
    }
}
