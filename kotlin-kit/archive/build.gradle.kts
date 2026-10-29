// Archive formats and their codecs: tar and zip are read and written here, gzip and deflate run on K/N's zlib, and
// bzip2 and xz on the reference C libraries. Conan fetches those (recipes pinned by conan.lock, see
// gradle/conan.gradle.kts) and cinterop links them into the klib, so nothing needs them at run time.
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.plugin.getKotlinPluginVersion
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget

val linkedTargets = setOf("macosArm64", "linuxX64", "mingwX64")
extra["conanTargets"] = linkedTargets
extra["kotlinVersion"] = getKotlinPluginVersion(logger)
apply(from = rootProject.file("gradle/conan.gradle.kts"))

/** Where Conan deploys both libraries for [target]: `bzip2/{include,lib}` and `xz_utils/{include,lib}` below it. */
fun deployed(target: String) = layout.buildDirectory.dir("conan/$target/direct_deploy")

configure<KotlinMultiplatformExtension> {
    sourceSets.commonMain.dependencies { api("org.jetbrains.kotlinx:kotlinx-io-core:0.9.1") }
    targets.withType<KotlinNativeTarget>().configureEach {
        val target = name
        // ConanCenter has no linuxArm64 binaries: that target compiles against the headers alone until it gets a
        // Conan build of its own.
        val linked = target in linkedTargets
        val source = if (linked) target else "macosArm64"
        val cinterop =
            compilations.getByName("main").cinterops.create("codecs") {
                definitionFile.set(project.file("src/nativeInterop/cinterop/codecs.def"))
                val root = deployed(source).get().asFile
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
        tasks.named(cinterop.interopProcessingTaskName) { dependsOn("conanInstall${source.replaceFirstChar(Char::uppercase)}") }
    }
}
