// Shared Gradle build settings, consumed as an included build:
//   settings.gradle.kts: pluginManagement { includeBuild("../ci-diffuser") }
//   build.gradle.kts:    plugins { id("ci.diffuser.native") }
// To rename: change rootProject.name, the plugin ids in build.gradle.kts and the `ci.diffuser` package.
rootProject.name = "ci-diffuser"
dependencyResolutionManagement {
    repositories { mavenCentral(); gradlePluginPortal() }
}
