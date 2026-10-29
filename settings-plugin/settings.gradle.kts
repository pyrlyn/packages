// Settings plugin in its own included build: settings plugins load in the settings classloader, which has no
// Kotlin Gradle plugin, so it must not share a jar with the project plugins of ../ (shared.settings.native etc.).
rootProject.name = "shared-settings-plugin"
dependencyResolutionManagement {
    repositories { mavenCentral(); gradlePluginPortal() }
    versionCatalogs { create("libs") { from(files("../gradle/libs.versions.toml")) } }
}
