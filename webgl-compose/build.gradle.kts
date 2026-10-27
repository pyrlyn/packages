import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension

// A Composable hosting a WebGL2 canvas over the Compose viewport, with resize, pointer and wheel events.
plugins {
    id("org.jetbrains.compose")
    kotlin("plugin.compose")
}

configure<KotlinMultiplatformExtension> {
    sourceSets.getByName("wasmJsMain").dependencies {
        api(project(":webgl"))
        implementation("org.jetbrains.compose.runtime:runtime:1.12.2")
        implementation("org.jetbrains.compose.ui:ui:1.12.2")
    }
}

// The module has no Compose UI tests (it is a library, not an executable), so the plugin's bundling check is moot.
tasks.matching { it.name.startsWith("checkComposeUiTestConfiguration") }.configureEach { enabled = false }
