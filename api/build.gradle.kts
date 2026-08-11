plugins {
    id("org.jetbrains.kotlin.jvm")
    id("org.jetbrains.kotlin.plugin.serialization")
    id("io.ktor.plugin")
    application
}

group = "com.hiddennoir"
version = "0.2.0-dev"

application {
    mainClass.set("com.hiddennoir.api.ApplicationKt")
}

kotlin { jvmToolchain(17) }

dependencies {
    implementation("io.ktor:ktor-server-core-jvm:3.5.2")
    implementation("io.ktor:ktor-server-netty-jvm:3.5.2")
    implementation("io.ktor:ktor-server-content-negotiation-jvm:3.5.2")
    implementation("io.ktor:ktor-serialization-kotlinx-json-jvm:3.5.2")
    implementation("io.ktor:ktor-server-call-logging-jvm:3.5.2")
    implementation("io.ktor:ktor-server-status-pages-jvm:3.5.2")

    implementation("org.flywaydb:flyway-core:13.2.0")
    implementation("org.flywaydb:flyway-database-postgresql:13.2.0")
    implementation("org.postgresql:postgresql:42.7.13")
    implementation("io.lettuce:lettuce-core:7.6.0.RELEASE")
    implementation("ch.qos.logback:logback-classic:1.5.18")

    testImplementation("io.ktor:ktor-server-test-host-jvm:3.5.2")
    testImplementation(kotlin("test"))
}
