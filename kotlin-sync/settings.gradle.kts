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
    // Shared builds live in the workspace's packages/. Every ancestor is searched, so the build works
    // from the main checkout and from a worktree nested deeper (e.g. .rtok/worktrees/); an env variable
    // points elsewhere. Defined here because pluginManagement cannot see the rest of the script.
    fun sharedBuild(name: String, env: String): String =
        System.getenv(env)?.let { settingsDir.resolve(it).path }
            ?: generateSequence(settingsDir.parentFile) { it.parentFile }
                .map { it.resolve("packages/$name") }
                .firstOrNull { it.isDirectory }
                ?.path
            ?: error("packages/$name not found above $settingsDir; set $env")

    // Shared build settings (ktlint, detekt, repositories, build cache).
    val sharedSettings = sharedBuild("shared-settings", "SHARED_SETTINGS_DIR")
    includeBuild(sharedSettings)
    includeBuild("$sharedSettings/settings-plugin")
    extra["kotlinKitDir"] = sharedBuild("kotlin-kit", "KOTLIN_KIT_DIR")
}

plugins {
    id("shared.settings")
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
includeBuild(extra["kotlinKitDir"] as String)

include(":proto", ":client", ":testing")
