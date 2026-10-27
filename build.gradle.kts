// Reusable WebGL2 for Kotlin/Wasm: a typed wrapper (:webgl), a Compose host (:webgl-compose) and a generic
// 2D graph/cloud renderer (:graph). ktlint and detekt come from ci-diffuser, as in the consumers.
plugins {
    kotlin("multiplatform") version "2.4.21" apply false
    kotlin("plugin.compose") version "2.4.21" apply false
    id("org.jetbrains.compose") version "1.12.2" apply false
    id("ci.diffuser.ktlint") apply false
    id("ci.diffuser.detekt") apply false
}

// Consumers include this build and name `pyrlyn.webgl:<module>`; Gradle substitutes the projects, nothing is published.
subprojects {
    group = "pyrlyn.webgl"
    pluginManager.apply("org.jetbrains.kotlin.multiplatform")
    pluginManager.apply("ci.diffuser.ktlint")
    pluginManager.apply("ci.diffuser.detekt")
    configure<org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension> {
        jvmToolchain(21)
        @OptIn(org.jetbrains.kotlin.gradle.ExperimentalWasmDsl::class)
        wasmJs { browser { testTask { enabled = false } } }
        sourceSets.commonTest.dependencies { implementation(kotlin("test")) }
    }
}
