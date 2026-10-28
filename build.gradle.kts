// ktlint and detekt come from ci-diffuser, so this package lints like the projects that use it.
plugins {
    alias(libs.plugins.kotlin.multiplatform) apply false
    alias(libs.plugins.kotlinx.rpc) apply false
    id("ci.diffuser.ktlint") apply false
    id("ci.diffuser.detekt") apply false
}

// Consumers include this build and name `pyrlyn.sync:<module>`; Gradle substitutes the projects by
// group and name, so nothing is published.
subprojects {
    group = "pyrlyn.sync"
    pluginManager.apply("org.jetbrains.kotlin.multiplatform")
    pluginManager.apply("ci.diffuser.ktlint")
    pluginManager.apply("ci.diffuser.detekt")
    configure<org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension> {
        // The targets kotlinx-rpc's gRPC builds for, and the ones backend-kotlin's servers run on.
        macosArm64()
        linuxX64()
        linuxArm64()
        compilerOptions { allWarningsAsErrors.set(true) }
        sourceSets.commonTest.dependencies { implementation(kotlin("test")) }
    }
}
