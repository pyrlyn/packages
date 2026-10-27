// Archive formats and their codecs: tar and zip are read and written here, gzip and deflate run on K/N's zlib, and
// bzip2 and xz on the reference C libraries. Conan fetches those (recipes pinned by conan.lock, ConanCenter's
// prebuilt static binaries only) and cinterop links them into the klib, so nothing needs them at run time.
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget

val conanDir = layout.projectDirectory
val profiles = mapOf("macosArm64" to "macosArm64.profile", "linuxX64" to "linuxX64.profile")

/** Where Conan deploys both libraries for [target]: `direct_deploy/bzip2/{include,lib}` and `direct_deploy/xz_utils/{include,lib}` below it. */
fun deployed(target: String) = layout.buildDirectory.dir("conan/$target")

val conanInstall =
    profiles.mapValues { (target, profile) ->
        tasks.register<Exec>("conanInstall${target.replaceFirstChar(Char::uppercase)}") {
            val out = deployed(target)
            inputs.files(conanDir.file("conanfile.txt"), conanDir.file("conan.lock"), conanDir.file("conan/$profile"))
            outputs.dir(out)
            commandLine(
                "conan",
                "install",
                conanDir.asFile.path,
                "--lockfile=${conanDir.file("conan.lock").asFile.path}",
                "-pr:h=${conanDir.file("conan/$profile").asFile.path}",
                "-pr:b=${conanDir.file("conan/macosArm64.profile").asFile.path}",
                "--build=never",
                "--deployer=direct_deploy",
                "--deployer-folder=${out.get().asFile.path}",
                "--output-folder=${out.get().asFile.path}/generators",
            )
        }
    }

configure<KotlinMultiplatformExtension> {
    sourceSets.commonMain.dependencies { api("org.jetbrains.kotlinx:kotlinx-io-core:0.9.1") }
    targets.withType<KotlinNativeTarget>().configureEach {
        val target = name
        // ConanCenter has no linuxArm64 binaries: that target compiles against the headers alone until it gets a
        // Conan build of its own.
        val linked = target in profiles
        val source = if (linked) target else "macosArm64"
        val cinterop =
            compilations.getByName("main").cinterops.create("codecs") {
                definitionFile.set(project.file("src/nativeInterop/cinterop/codecs.def"))
                val root = deployed(source).get().asFile.resolve("direct_deploy")
                includeDirs(root.resolve("bzip2/include"), root.resolve("xz_utils/include"))
                if (linked) {
                    extraOpts(
                        "-libraryPath",
                        root.resolve("bzip2/lib").path,
                        "-libraryPath",
                        root.resolve("xz_utils/lib").path,
                        "-staticLibrary",
                        "libbz2.a",
                        "-staticLibrary",
                        "liblzma.a",
                    )
                }
            }
        tasks.named(cinterop.interopProcessingTaskName) { dependsOn(conanInstall.getValue(source)) }
    }
}
