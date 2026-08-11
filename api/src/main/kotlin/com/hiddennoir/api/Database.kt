package com.hiddennoir.api

import org.flywaydb.core.Flyway

fun migrateDatabaseFromEnvironment() {
    val url = System.getenv("DATABASE_URL")
        ?: "jdbc:postgresql://localhost:5432/hiddennoir"
    val user = System.getenv("DATABASE_USER") ?: "hiddennoir"
    val password = System.getenv("DATABASE_PASSWORD") ?: "hiddennoir"

    Flyway.configure()
        .dataSource(url, user, password)
        .locations("classpath:db/migration")
        .load()
        .migrate()
}
