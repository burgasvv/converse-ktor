
@file:Suppress("VulnerableLibrariesLocal")

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(ktorLibs.plugins.ktor)
    alias(libs.plugins.kotlin.serialization)
}

group = "org.burgas"
version = "1.0.0-SNAPSHOT"

application {
    mainClass = "io.ktor.server.netty.EngineMain"
}

kotlin {
    jvmToolchain(25)
}

repositories {
    mavenCentral()
}

dependencies {
    implementation(ktorLibs.serialization.kotlinx.json)
    implementation(ktorLibs.server.config.yaml)
    implementation(ktorLibs.server.contentNegotiation)
    implementation(ktorLibs.server.core)
    implementation(ktorLibs.server.netty)
    implementation(libs.logback.classic)
    implementation("io.ktor:ktor-server-sessions:3.4.0")
    implementation("io.ktor:ktor-server-host-common:3.4.0")
    implementation("io.ktor:ktor-server-status-pages:3.4.0")
    implementation("io.ktor:ktor-server-cors:3.4.0")
    implementation("io.ktor:ktor-server-csrf:3.4.0")
    implementation("org.jetbrains.exposed:exposed-core:0.61.0")
    implementation("org.jetbrains.exposed:exposed-jdbc:0.61.0")
    implementation("org.jetbrains.exposed:exposed-dao:0.61.0")
    implementation("org.jetbrains.exposed:exposed-kotlin-datetime:0.61.0")
    implementation("org.postgresql:postgresql:42.7.10")
    implementation("com.zaxxer:HikariCP:6.2.1")
    implementation("redis.clients:jedis:5.1.2")

    testImplementation(kotlin("test"))
    testImplementation(ktorLibs.server.testHost)
}
