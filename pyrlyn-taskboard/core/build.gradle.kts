// The model, the Store SPI and the engine behind Board. Platform-free: stores live in other modules.
configure<org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension> {
    sourceSets {
        commonMain.dependencies {
            api("org.jetbrains.kotlinx:kotlinx-serialization-json:1.12.0")
            api("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.11.0")
            implementation("org.kotlincrypto.hash:sha2:0.8.0")
        }
        // The engine tests run against the in-memory store and the race kit.
        commonTest.dependencies { implementation(project(":testing")) }
    }
}
