// Child processes under a deadline, with bounded output and their whole tree to kill: a process group on POSIX,
// a job object on Windows. Each system's spawning lives in its own C shim.
configure<org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension> {
    targets.withType<org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget>().configureEach {
        val shim = if (name == "mingwX64") "spawn_win" else "spawn"
        compilations.getByName("main").cinterops.create(shim) {
            definitionFile.set(project.file("src/nativeInterop/cinterop/$shim.def"))
        }
    }
}
