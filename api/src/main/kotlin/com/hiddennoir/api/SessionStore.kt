package com.hiddennoir.api

import io.lettuce.core.RedisClient
import java.security.SecureRandom
import java.util.Base64
import java.util.concurrent.ConcurrentHashMap

interface SessionStore : AutoCloseable {
    fun createSession(ttlSeconds: Long): String
    fun isValid(token: String): Boolean
    override fun close() {}
}

class InMemorySessionStore : SessionStore {
    private val sessions = ConcurrentHashMap<String, Long>()

    override fun createSession(ttlSeconds: Long): String {
        val token = randomToken()
        sessions[token] = System.currentTimeMillis() + ttlSeconds * 1000
        return token
    }

    override fun isValid(token: String): Boolean {
        val expiry = sessions[token] ?: return false
        if (expiry < System.currentTimeMillis()) {
            sessions.remove(token)
            return false
        }
        return true
    }
}

class RedisSessionStore(host: String, port: Int) : SessionStore {
    private val client = RedisClient.create("redis://$host:$port")
    private val connection = client.connect()
    private val commands = connection.sync()

    override fun createSession(ttlSeconds: Long): String {
        val token = randomToken()
        commands.setex("anonymous_session:$token", ttlSeconds, "1")
        return token
    }

    override fun isValid(token: String): Boolean =
        commands.exists("anonymous_session:$token") == 1L

    override fun close() {
        connection.close()
        client.shutdown()
    }
}

private fun randomToken(): String {
    val bytes = ByteArray(32)
    SecureRandom().nextBytes(bytes)
    return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
}
