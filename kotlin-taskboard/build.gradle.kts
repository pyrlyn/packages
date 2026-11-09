// ktlint and detekt come from shared-settings, so this package lints like the projects that use it.
plugins {
    kotlin("multiplatform") version "2.4.21" apply false
    id("shared.settings.ktlint") apply false
    id("shared.settings.detekt") apply false
}

// Consumers include this build and name `pyrlyn.taskboard:<module>`; Gradle substitutes the projects by
// group and name, so nothing is published.
subprojects {
    group = "pyrlyn.taskboard"
    pluginManager.apply("org.jetbrains.kotlin.multiplatform")
    pluginManager.apply("shared.settings.ktlint")
    pluginManager.apply("shared.settings.detekt")
    configure<org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension> {
        // The native targets rtok and the other packages build. The race tests need real threads, which the
        // native targets give; a JVM or wasm target waits for a consumer that needs one.
        macosArm64()
        linuxX64()
        linuxArm64()
        mingwX64()
        compilerOptions { allWarningsAsErrors.set(true) }
        sourceSets.commonTest.dependencies { implementation(kotlin("test")) }
    }
}
