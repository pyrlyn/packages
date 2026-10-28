// ktlint and detekt come from ci-diffuser, so this package lints like the projects that use it.
plugins {
    kotlin("multiplatform") version "2.4.21" apply false
    id("ci.diffuser.ktlint") apply false
    id("ci.diffuser.detekt") apply false
}

// Consumers include this build and name `pyrlyn.kit:<module>`; Gradle substitutes the projects by
// group and name, so nothing is published.
subprojects {
    group = "pyrlyn.kit"
    pluginManager.apply("org.jetbrains.kotlin.multiplatform")
    pluginManager.apply("ci.diffuser.ktlint")
    pluginManager.apply("ci.diffuser.detekt")
    configure<org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension> {
        // The native targets the consumers build. The code came from native-only rtok-kotlin and its
        // tests use native APIs; a JVM target waits for a consumer that needs one. ketch builds for Windows.
        macosArm64()
        linuxX64()
        linuxArm64()
        mingwX64()
        // unixMain holds the POSIX code macOS and Linux share; mingwMain the Windows side of the same API.
        applyDefaultHierarchyTemplate {
            common {
                group("native") {
                    group("unix") {
                        group("apple")
                        group("linux")
                    }
                }
            }
        }
        compilerOptions { allWarningsAsErrors.set(true) }
        sourceSets.commonTest.dependencies { implementation(kotlin("test")) }
    }
}
