plugins {
    kotlin("jvm") version "2.4.21"
    id("org.jetbrains.compose") version "1.12.2"
    id("org.jetbrains.kotlin.plugin.compose") version "2.4.21"
}

group = "dk.kodeologi.azure.keyvault.viewer"
version = "1.0.0"

repositories {
    mavenCentral()
    google()
}

dependencies {
    implementation(compose.desktop.currentOs)
    implementation("org.jetbrains.compose.material3:material3:1.9.0")
    implementation("com.azure:azure-identity:1.19.0")
    implementation("com.squareup.okhttp3:okhttp:5.5.1")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.23.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.11.0")

    testImplementation(kotlin("test"))
    testImplementation("io.mockk:mockk:1.14.12")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.11.0")
}

kotlin {
    jvmToolchain(25)
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21)
    }
}

compose.desktop {
    application {
        mainClass = "dk.kodeologi.azure.keyvault.viewer.MainKt"
    }
}

tasks.withType<JavaCompile>().configureEach {
    options.release.set(21)
}

tasks.test {
    useJUnitPlatform()
}
