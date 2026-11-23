import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension

// Components in commonMain; only the bundled fonts are per platform. No Material 3: consumers such as Octo
// keep their own controls on Compose foundation, and these components need nothing more.
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
