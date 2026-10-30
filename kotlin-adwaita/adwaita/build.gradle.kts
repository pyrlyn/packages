import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension

// Adwaita-style components in commonMain only: a platform needs no code of its own to use them.
plugins {
    id("org.jetbrains.compose")
    kotlin("plugin.compose")
}

configure<KotlinMultiplatformExtension> {
    sourceSets {
        commonMain.dependencies {
            api(compose.runtime)
            api(compose.foundation)
            api(compose.ui)
            api(compose.material3)
            api("pyrlyn.glass:glass")
        }
        jvmTest.dependencies {
            implementation(compose.desktop.currentOs)
            @OptIn(org.jetbrains.compose.ExperimentalComposeLibrary::class)
            implementation(compose.uiTest)
        }
    }
}

// UI tests run on jvm; wasmJs is here to compile, so it needs no bundled test runtime.
tasks.matching { it.name == "checkComposeUiTestConfigurationForWasmJs" }.configureEach { enabled = false }
