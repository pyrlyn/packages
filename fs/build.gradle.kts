// The port of the fs-kit crate: atomic writes, reads where missing is empty, tree copy and removal.
configure<org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension> {
    sourceSets.commonMain.dependencies { api("org.jetbrains.kotlinx:kotlinx-io-core:0.9.1") }
}
