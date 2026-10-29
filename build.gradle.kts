// ktlint and detekt come from shared-settings, so this package lints like the projects that use it.
plugins {
    kotlin("multiplatform") version "2.4.21" apply false
    id("shared.settings.ktlint") apply false
    id("shared.settings.detekt") apply false
}

// Consumers include this build and name `pyrlyn.snapshot:snapshot`; Gradle
// substitutes the projects by group and name, so nothing is published.
subprojects {
    group = "pyrlyn.snapshot"
    pluginManager.apply("org.jetbrains.kotlin.multiplatform")
    pluginManager.apply("shared.settings.ktlint")
    pluginManager.apply("shared.settings.detekt")
    configure<org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension> {
        jvmToolchain(21)
        // Every target any consumer builds; a missing one would fail that consumer's resolution.
        jvm()
        macosArm64()
        linuxX64()
        linuxArm64()
        mingwX64()
        // Compose Multiplatform web tests run on Node, where snapshot files go through `fs`.
        @OptIn(org.jetbrains.kotlin.gradle.ExperimentalWasmDsl::class)
        wasmJs { nodejs() }
        compilerOptions { allWarningsAsErrors.set(true) }
        sourceSets.commonTest.dependencies { implementation(kotlin("test")) }
    }
}
