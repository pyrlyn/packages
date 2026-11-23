// Reusable Adwaita-style components for Compose Multiplatform. ktlint and detekt come from shared-settings, as in the consumers.
plugins {
    kotlin("multiplatform") version "2.4.21" apply false
    kotlin("plugin.compose") version "2.4.21" apply false
    id("org.jetbrains.compose") version "1.12.2" apply false
    id("shared.settings.ktlint") apply false
    id("shared.settings.detekt") apply false
}

// Consumers include this build and name `pyrlyn.adwaita:<module>`; Gradle substitutes the projects, nothing is published.
subprojects {
    group = "pyrlyn.adwaita"
    pluginManager.apply("org.jetbrains.kotlin.multiplatform")
    pluginManager.apply("shared.settings.ktlint")
    pluginManager.apply("shared.settings.detekt")
    configure<org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension> {
        jvmToolchain(21)
        jvm()
        @OptIn(org.jetbrains.kotlin.gradle.ExperimentalWasmDsl::class)
        wasmJs { browser { testTask { enabled = false } } }
        compilerOptions { allWarningsAsErrors.set(true) }
        sourceSets.commonTest.dependencies { implementation(kotlin("test")) }
    }
}
