package shared.settings

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget
import org.jetbrains.kotlin.gradle.plugin.mpp.NativeBuildType
import org.jetbrains.kotlin.gradle.plugin.mpp.TestExecutable
import org.jetbrains.kotlin.konan.target.Family

/**
 * Applied once in the root build script; configures every subproject that applies
 * `org.jetbrains.kotlin.multiplatform`.
 *
 * Properties (each `diffuser.<name>`, falling back to the legacy `rtok.<name>`):
 * - `allTargets=true`       add linuxX64 and linuxArm64 next to macosArm64.
 * - `releaseSize=none|all|small,latin1,strip`  release-only size options (default `none`).
 * - `release.fast=true`     K/N release binary cache: fast release links, no whole-program LTO. Never ship.
 * Every Kotlin module (multiplatform or jvm) also gets [KtlintConventionPlugin] and [DetektConventionPlugin].
 * Release link tasks never enter the build cache. Debug and test binaries are untouched.
 */
class NativeConventionPlugin : Plugin<Project> {
    override fun apply(root: Project) {
        fun prop(name: String): String? =
            root.providers.gradleProperty("diffuser.$name").orNull ?: root.providers.gradleProperty("rtok.$name").orNull

        val allTargets = prop("allTargets") == "true"
        val releaseFast = prop("release.fast") == "true"
        val sizeOpts = (prop("releaseSize") ?: "none").split(',').map { it.trim() }.toSet()
        fun size(o: String) = "all" in sizeOpts || o in sizeOpts

        root.subprojects {
            val p = this
            // KGP reads this before it configures native link tasks, so set it before the plugin is applied.
            if (releaseFast) p.extensions.extraProperties["kotlin.internal.native.enableReleaseBinaryCache"] = "true"
            for (kotlinPlugin in listOf("org.jetbrains.kotlin.multiplatform", "org.jetbrains.kotlin.jvm")) {
                p.pluginManager.withPlugin(kotlinPlugin) {
                    p.pluginManager.apply(KtlintConventionPlugin::class.java)
                    p.pluginManager.apply(DetektConventionPlugin::class.java)
                }
            }
            p.pluginManager.withPlugin("org.jetbrains.kotlin.multiplatform") {
                p.tasks.matching { it.name.startsWith("linkRelease") }.configureEach {
                    outputs.cacheIf("release binaries are not cached") { false }
                }
                p.extensions.configure(KotlinMultiplatformExtension::class.java) {
                    macosArm64()
                    if (allTargets) { linuxX64(); linuxArm64() }
                    targets.withType(KotlinNativeTarget::class.java).configureEach {
                        binaries.matching { it.buildType == NativeBuildType.RELEASE && it !is TestExecutable }.configureEach {
                            if (releaseFast) binaryOption("enableReleaseBinaryCache", "true")
                            if (size("small")) freeCompilerArgs += "-Xbinary=smallBinary=true"
                            if (size("latin1")) freeCompilerArgs += "-Xbinary=latin1Strings=true"
                            if (size("strip")) linkerOpts(
                                if (konanTarget.family == Family.OSX) listOf("-Wl,-dead_strip", "-Wl,-x", "-Wl,-S")
                                else listOf("-Wl,--gc-sections", "-Wl,-s")
                            )
                        }
                    }
                }
            }
        }
    }
}
