package com.hiddennoir.api

import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import java.sql.Connection
import java.sql.DriverManager
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.HexFormat
import java.util.UUID

class HiddenNoirDatabase(
    databasePath: String
) {
    private val jdbcUrl: String

    init {
        val path = Path.of(databasePath)
        path.parent?.let { Files.createDirectories(it) }

        jdbcUrl = "jdbc:sqlite:${path.toAbsolutePath()}"

        Class.forName("org.sqlite.JDBC")
        initializeSchema()
    }

    private fun connection(): Connection =
        DriverManager.getConnection(jdbcUrl)

    private fun initializeSchema() {
        connection().use { connection ->
            connection.createStatement().use { statement ->
                statement.execute("PRAGMA journal_mode=WAL")
                statement.execute("PRAGMA foreign_keys=ON")

                statement.execute(
                    """
                    CREATE TABLE IF NOT EXISTS sessions (
                        token_hash TEXT PRIMARY KEY,
                        created_at INTEGER NOT NULL,
                        expires_at INTEGER NOT NULL
                    )
                    """.trimIndent()
                )

                statement.execute(
                    """
                    CREATE TABLE IF NOT EXISTS generation_events (
                        id TEXT PRIMARY KEY,
                        session_hash TEXT NOT NULL,
                        mode TEXT NOT NULL,
                        input_character_count INTEGER NOT NULL,
                        returned_reply_count INTEGER NOT NULL,
                        policy_result TEXT NOT NULL,
                        model_version TEXT NOT NULL,
                        latency_ms INTEGER NOT NULL,
                        created_at INTEGER NOT NULL
                    )
                    """.trimIndent()
                )

                statement.execute(
                    """
                    CREATE INDEX IF NOT EXISTS idx_generation_events_session_created
                    ON generation_events(session_hash, created_at)
                    """.trimIndent()
                )
            }
        }
    }

    fun createSession(token: String, ttlSeconds: Long) {
        val now = Instant.now().epochSecond
        val expiresAt = now + ttlSeconds

        connection().use { connection ->
            connection.prepareStatement(
                """
                INSERT INTO sessions(token_hash, created_at, expires_at)
                VALUES (?, ?, ?)
                """.trimIndent()
            ).use { statement ->
                statement.setString(1, hashToken(token))
                statement.setLong(2, now)
                statement.setLong(3, expiresAt)
                statement.executeUpdate()
            }
        }
    }

    fun isSessionValid(token: String): Boolean {
        val now = Instant.now().epochSecond

        connection().use { connection ->
            connection.prepareStatement(
                """
                SELECT 1
                FROM sessions
                WHERE token_hash = ?
                  AND expires_at > ?
                LIMIT 1
                """.trimIndent()
            ).use { statement ->
                statement.setString(1, hashToken(token))
                statement.setLong(2, now)

                statement.executeQuery().use { result ->
                    return result.next()
                }
            }
        }
    }

    fun generationCountToday(token: String): Int {
        val threshold =
            Instant.now()
                .truncatedTo(ChronoUnit.DAYS)
                .epochSecond

        connection().use { connection ->
            connection.prepareStatement(
                """
                SELECT COUNT(*)
                FROM generation_events
                WHERE session_hash = ?
                  AND created_at >= ?
                  AND policy_result = 'allowed'
                """.trimIndent()
            ).use { statement ->
                statement.setString(1, hashToken(token))
                statement.setLong(2, threshold)

                statement.executeQuery().use { result ->
                    return if (result.next()) result.getInt(1) else 0
                }
            }
        }
    }

    fun recordGeneration(
        token: String,
        mode: String,
        inputCharacterCount: Int,
        returnedReplyCount: Int,
        policyResult: String,
        modelVersion: String,
        latencyMs: Long
    ) {
        connection().use { connection ->
            connection.prepareStatement(
                """
                INSERT INTO generation_events(
                    id,
                    session_hash,
                    mode,
                    input_character_count,
                    returned_reply_count,
                    policy_result,
                    model_version,
                    latency_ms,
                    created_at
                )
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """.trimIndent()
            ).use { statement ->
                statement.setString(1, UUID.randomUUID().toString())
                statement.setString(2, hashToken(token))
                statement.setString(3, mode)
                statement.setInt(4, inputCharacterCount)
                statement.setInt(5, returnedReplyCount)
                statement.setString(6, policyResult)
                statement.setString(7, modelVersion)
                statement.setLong(8, latencyMs)
                statement.setLong(9, Instant.now().epochSecond)
                statement.executeUpdate()
            }
        }
    }

    private fun hashToken(token: String): String {
        val digest =
            MessageDigest.getInstance("SHA-256")
                .digest(token.toByteArray(Charsets.UTF_8))

        return HexFormat.of().formatHex(digest)
    }
}
