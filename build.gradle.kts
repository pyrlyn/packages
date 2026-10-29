// ktlint and detekt come from shared-settings, so this package lints like the projects that use it.
plugins {
    alias(libs.plugins.kotlin.multiplatform) apply false
    alias(libs.plugins.kotlinx.rpc) apply false
    id("shared.settings.ktlint") apply false
    id("shared.settings.detekt") apply false
}

// Consumers include this build and name `pyrlyn.sync:<module>`; Gradle substitutes the projects by
// group and name, so nothing is published.
subprojects {
    group = "pyrlyn.sync"
    pluginManager.apply("org.jetbrains.kotlin.multiplatform")
    pluginManager.apply("shared.settings.ktlint")
    pluginManager.apply("shared.settings.detekt")
    configure<org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension> {
        // The targets kotlinx-rpc's gRPC builds for, and the ones backend-kotlin's servers run on.
        macosArm64()
        linuxX64()
        linuxArm64()
        compilerOptions { allWarningsAsErrors.set(true) }
        sourceSets.commonTest.dependencies { implementation(kotlin("test")) }
    }
}
