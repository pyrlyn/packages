pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
        google()
    }
    // Shared build settings from a checkout of ci-diffuser; CI_DIFFUSER_DIR points elsewhere.
    val ciDiffuser = System.getenv("CI_DIFFUSER_DIR") ?: "../../apps/ci-diffuser"
    includeBuild(ciDiffuser)
    includeBuild("$ciDiffuser/settings-plugin")
}

plugins {
    id("ci.diffuser.settings")
}

rootProject.name = "kotlin-webgl"

include(":webgl", ":webgl-compose", ":graph")
