// Child processes under a deadline, with bounded output and a process group to kill; POSIX only.
configure<org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension> {
    targets.withType<org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget>().configureEach {
        compilations.getByName("main").cinterops.create("spawn") {
            definitionFile.set(project.file("src/nativeInterop/cinterop/spawn.def"))
        }
    }
}
