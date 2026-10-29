// Shared Gradle build settings, consumed as an included build:
//   settings.gradle.kts: pluginManagement { includeBuild(<packages/shared-settings>) }, see README.md
//   build.gradle.kts:    plugins { id("shared.settings.native") }
// To rename: change rootProject.name, the plugin ids in build.gradle.kts and the `shared.settings` package.
rootProject.name = "shared-settings"
dependencyResolutionManagement {
    repositories { mavenCentral(); gradlePluginPortal() }
}
