import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension

// Thin typed WebGL2 wrapper for Kotlin/Wasm: context, shaders and programs, buffers, vertex arrays, draw calls.
configure<KotlinMultiplatformExtension> {
    sourceSets.getByName("wasmJsMain").dependencies { api("org.jetbrains.kotlinx:kotlinx-browser:0.5.0") }
}
