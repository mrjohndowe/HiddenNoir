package com.hiddennoir.api

import java.security.SecureRandom
import java.util.Base64

class SessionService(
    private val database: HiddenNoirDatabase
) {
    fun createSession(ttlSeconds: Long): String {
        val bytes = ByteArray(32)
        SecureRandom().nextBytes(bytes)

        val token =
            Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(bytes)

        database.createSession(token, ttlSeconds)
        return token
    }

    fun isValid(token: String): Boolean =
        database.isSessionValid(token)
}
