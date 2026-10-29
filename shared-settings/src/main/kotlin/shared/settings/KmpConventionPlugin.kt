package shared.settings

import kotlinx.kover.gradle.plugin.dsl.KoverProjectExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension

/**
 * Applied once in the root build script (next to `shared.settings.native`). The consumer declares the plugins with
 * versions in its root `plugins {}` block (`apply false`): kotlin multiplatform + serialization, dokka, ksp, kotest, kover.
 *
 * Every subproject gets: multiplatform, serialization, dokka, ksp, kotest; commonMain kotlinx-serialization-json;
 * commonTest kotlin-test + kotest engine/assertions. The root aggregates Dokka over all subprojects.
 *
 * Properties (`diffuser.<name>`, fallback `rtok.<name>`):
 * - `coverage=true`              dev-only Kover: adds a JVM target to `coverage.modules` and aggregates reports
 *                                into build/reports/coverage/jvm-html and jvm.xml. Never for product builds.
 * - `coverage.modules=a,b,c`     subprojects whose sources are all common (JVM-buildable).
 */
class KmpConventionPlugin : Plugin<Project> {
    override fun apply(root: Project) {
        fun prop(name: String): String? =
            root.providers.gradleProperty("diffuser.$name").orNull ?: root.providers.gradleProperty("rtok.$name").orNull
        val coverage = prop("coverage") == "true"
        val coverable = (prop("coverage.modules") ?: "").split(',').map { it.trim() }.filter { it.isNotEmpty() }.toSet()

        root.pluginManager.apply("org.jetbrains.dokka")
        root.subprojects.forEach { root.dependencies.add("dokka", it) }
        if (coverage) {
            root.pluginManager.apply("org.jetbrains.kotlinx.kover")
            coverable.forEach { root.dependencies.add("kover", root.project(":$it")) }
            root.extensions.configure(KoverProjectExtension::class.java) {
                reports.total.html.htmlDir.set(root.layout.buildDirectory.dir("reports/coverage/jvm-html"))
                reports.total.xml.xmlFile.set(root.layout.buildDirectory.file("reports/coverage/jvm.xml"))
            }
        }
        root.subprojects {
            val p = this
            listOf(
                "org.jetbrains.kotlin.multiplatform", "org.jetbrains.kotlin.plugin.serialization",
                "org.jetbrains.dokka", "com.google.devtools.ksp", "io.kotest",
            ).forEach { p.pluginManager.apply(it) }
            val jvmCov = coverage && p.name in coverable
            if (jvmCov) p.pluginManager.apply("org.jetbrains.kotlinx.kover")
            p.extensions.configure(KotlinMultiplatformExtension::class.java) {
                if (jvmCov) jvm()
                sourceSets.getByName("commonMain").dependencies {
                    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:${Versions.SERIALIZATION_JSON}")
                }
                sourceSets.getByName("commonTest").dependencies {
                    implementation("org.jetbrains.kotlin:kotlin-test")
                    implementation("io.kotest:kotest-framework-engine:${Versions.KOTEST}")
                    implementation("io.kotest:kotest-assertions-core:${Versions.KOTEST}")
                }
            }
        }
    }
}
