import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension

// Fluent components in commonMain: a platform needs no code of its own to use them. jvmMain only adds Windows 11's frame.
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
        jvmMain.dependencies {
            implementation("net.java.dev.jna:jna:5.19.1")
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
