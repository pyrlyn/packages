// The port of the fs-kit crate: atomic writes, reads where missing is empty, tree copy and removal.
configure<org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension> {
    sourceSets.commonMain.dependencies { api("org.jetbrains.kotlinx:kotlinx-io-core:0.9.1") }
    // POSIX sets file times through a C shim; Windows wraps its calls in one so GetLastError is read in time.
    targets.withType<org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget>().configureEach {
        val shim = if (name == "mingwX64") "win" else "mtime"
        compilations.getByName("main").cinterops.create(shim) {
            definitionFile.set(project.file("src/nativeInterop/cinterop/$shim.def"))
        }
    }
}
