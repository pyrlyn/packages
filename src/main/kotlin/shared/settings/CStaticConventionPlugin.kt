package shared.settings

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.tasks.Exec
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget

/**
 * Builds a module's own C helpers into a static library and links it through cinterop `staticLibraries`.
 *
 * Usage in a module build script:
 * ```
 * plugins { id("shared.settings.cstatic") }
 * diffuserCStatic { library(name = "rtok_spawn", cinterop = "spawn") }
 * ```
 * Sources: `src/nativeInterop/c/<name>.c` (+ headers in the same dir). Output: `build/native/macosArm64/lib<name>.a`.
 * The `.def` file must list `staticLibraries = lib<name>.a`. macOS arm64 only (xcrun clang), like the default target set.
 */
class CStaticConventionPlugin : Plugin<Project> {
    override fun apply(project: Project) {
        project.extensions.create("diffuserCStatic", CStaticExtension::class.java, project)
    }
}

open class CStaticExtension(private val project: Project) {
    fun library(name: String, cinterop: String, minMacos: String = "12.0") {
        val cDir = project.layout.projectDirectory.dir("src/nativeInterop/c")
        val libDir = project.layout.buildDirectory.dir("native/macosArm64")
        val taskName = "compile" + name.split('_').joinToString("") { it.replaceFirstChar(Char::uppercase) } + "C"
        val compile = project.tasks.register(taskName, Exec::class.java) {
            inputs.dir(cDir)
            outputs.dir(libDir)
            val out = libDir.get().asFile
            val src = cDir.file("$name.c").asFile
            doFirst { out.mkdirs() }
            commandLine(
                "sh", "-c",
                "xcrun clang -c -O2 -arch arm64 -mmacosx-version-min=$minMacos -I'${cDir.asFile}' '$src' -o '$out/$name.o' " +
                    "&& xcrun ar rcs '$out/lib$name.a' '$out/$name.o'",
            )
        }
        project.extensions.configure(KotlinMultiplatformExtension::class.java) {
            targets.withType(KotlinNativeTarget::class.java).configureEach {
                compilations.getByName("main").cinterops.maybeCreate(cinterop).apply {
                    includeDirs(cDir)
                    extraOpts("-libraryPath", libDir.get().asFile.absolutePath)
                }
            }
        }
        val prefix = "cinterop" + cinterop.replaceFirstChar(Char::uppercase)
        project.tasks.matching { it.name.startsWith(prefix) }.configureEach { dependsOn(compile) }
    }
}
