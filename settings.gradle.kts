pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
        maven("https://redirector.kotlinlang.org/maven/kxrpc-grpc") {
            content {
                includeGroup("org.jetbrains.kotlinx.rpc.plugin")
                includeModuleByRegex("org\\.jetbrains\\.kotlinx", "kotlinx-rpc-.*")
            }
        }
    }
    // Shared build settings from a checkout of ci-diffuser; CI_DIFFUSER_DIR points elsewhere.
    val ciDiffuser = System.getenv("CI_DIFFUSER_DIR") ?: "../../apps/ci-diffuser"
    includeBuild(ciDiffuser)
    includeBuild("$ciDiffuser/settings-plugin")
}

plugins {
    id("ci.diffuser.settings")
}

// kotlinx-rpc publishes its gRPC artifacts to a JetBrains repository of their own; only those
// modules are looked up there.
dependencyResolutionManagement {
    repositories {
        maven("https://redirector.kotlinlang.org/maven/kxrpc-grpc") {
            content { includeModuleByRegex("org\\.jetbrains\\.kotlinx", "(kotlinx-rpc|protoc-gen)-.*") }
        }
    }
}

rootProject.name = "kotlin-sync"

// The HTTP interface device login talks through: a checkout of kotlin-kit.
includeBuild(System.getenv("KOTLIN_KIT_DIR") ?: "../kotlin-kit")

include(":proto", ":client", ":testing")
