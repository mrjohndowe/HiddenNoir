plugins {
    id("org.jetbrains.kotlin.jvm")
    id("org.jetbrains.kotlin.plugin.serialization")
    id("io.ktor.plugin")
    application
}

group = "com.hiddennoir"
version = "0.3.0-dev"

application {
    mainClass.set("com.hiddennoir.api.ApplicationKt")
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation("io.ktor:ktor-server-core-jvm:3.5.2")
    implementation("io.ktor:ktor-server-netty-jvm:3.5.2")
    implementation("io.ktor:ktor-server-content-negotiation-jvm:3.5.2")
    implementation("io.ktor:ktor-serialization-kotlinx-json-jvm:3.5.2")
    implementation("io.ktor:ktor-server-call-logging-jvm:3.5.2")
    implementation("io.ktor:ktor-server-status-pages-jvm:3.5.2")
    implementation("io.ktor:ktor-client-okhttp:3.5.2")
    implementation("io.ktor:ktor-client-core-jvm:3.5.2")
    implementation("io.ktor:ktor-client-cio-jvm:3.5.2")
    implementation("io.ktor:ktor-client-content-negotiation-jvm:3.5.2")
    implementation("io.ktor:ktor-serialization-kotlinx-json-jvm:3.5.2")
    implementation("io.ktor:ktor-client-core:3.5.2")

    implementation("org.xerial:sqlite-jdbc:3.53.4.0")
    implementation("ch.qos.logback:logback-classic:1.5.18")

    testImplementation("io.ktor:ktor-server-test-host-jvm:3.5.2")
    testImplementation(kotlin("test"))
}
