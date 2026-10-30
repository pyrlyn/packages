pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
        google()
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
    extra["kotlinGlassDir"] = sharedBuild("kotlin-glass", "KOTLIN_GLASS_DIR")
}

plugins {
    id("shared.settings")
}

rootProject.name = "kotlin-adwaita"

include(":adwaita")

// The glyphs and the auto-fit grid, which the looks share.
includeBuild(extra["kotlinGlassDir"] as String)
