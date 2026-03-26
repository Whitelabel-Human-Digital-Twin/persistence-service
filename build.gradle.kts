val kotlin_version: String by project
val logback_version: String by project
val mongo_version: String by project
val ktor_version: String by project
val swagger_codegen_version: String by project

plugins {
    kotlin("jvm") version "2.3.0"
    id("io.ktor.plugin") version "3.4.1"
    id("org.jetbrains.kotlin.plugin.serialization") version "2.3.0"
}

group = "io.github.whdt"
version = "0.0.1"

application {
    mainClass = "io.ktor.server.netty.EngineMain"
}

kotlin {
    jvmToolchain(23)
}

repositories {
    maven {
        url = uri("https://maven.pkg.github.com/Whitelabel-Human-Digital-Twin/whdt") // or the correct GitHub repo
        credentials {
            username = project.findProperty("gpr.user") as String? ?: System.getenv("GPR_USER")
            password = project.findProperty("gpr.key") as String? ?: System.getenv("GPR_TOKEN")
        }
    }
    mavenCentral()
}


ktor {
    openApi {
        enabled = true
        codeInferenceEnabled = false
        onlyCommented = false
    }
}

dependencies {
    implementation("io.ktor:ktor-server-core")
    implementation("io.ktor:ktor-server-content-negotiation")
    implementation("io.ktor:ktor-server-cors")
    implementation("io.ktor:ktor-serialization-kotlinx-json")
    implementation("org.mongodb:mongodb-driver-core:$mongo_version")
    implementation("org.mongodb:mongodb-driver-sync:$mongo_version")
    implementation("org.mongodb:bson:$mongo_version")
    implementation("io.ktor:ktor-server-netty")
    implementation("ch.qos.logback:logback-classic:$logback_version")
    implementation("io.ktor:ktor-server-config-yaml")
    implementation("io.ktor:ktor-server-openapi:$ktor_version")
    implementation("io.ktor:ktor-server-swagger:$ktor_version")
    implementation("io.swagger.codegen.v3:swagger-codegen-generators:${swagger_codegen_version}")
    testImplementation("io.ktor:ktor-server-test-host")
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit:$kotlin_version")

    implementation("io.github.whdt:whdt-core:0.6.1")
    implementation("io.github.whdt:whdt-distributed:0.3.0")
}
