package ci.diffuser

import dev.detekt.gradle.Detekt
import dev.detekt.gradle.extensions.DetektExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.tasks.testing.AbstractTestTask

/**
 * detekt (dev.detekt) for one Kotlin module.
 * Config: detekt defaults + shared ci-diffuser detekt.yml + optional `<root>/config/detekt/detekt.yml` override.
 * Baseline: `<module>/detekt-baseline.xml` (create/refresh with `detektBaseline`; missing file = no baseline).
 * Task `detekt` (source-only analysis, works for KMP/Kotlin Native) is wired into `check`, and every
 * test task depends on it, so it runs before tests. No detekt-formatting: formatting stays with ktlint.
 */
class DetektConventionPlugin : Plugin<Project> {
    override fun apply(project: Project) {
        project.pluginManager.apply("dev.detekt")
        val shared = project.rootProject.layout.buildDirectory.file("ci-diffuser/detekt.yml")
        val sharedFile = shared.get().asFile
        if (!sharedFile.exists() || sharedFile.readText() != SHARED) {
            sharedFile.parentFile.mkdirs()
            sharedFile.writeText(SHARED)
        }
        val override = project.rootProject.file("config/detekt/detekt.yml")
        project.extensions.configure(DetektExtension::class.java) {
            buildUponDefaultConfig.set(true)
            parallel.set(true)
            // KMP: the plain `detekt`/`detektBaseline` tasks default to src/main/kotlin; point them at every source set.
            source.setFrom(project.fileTree("src") { include("*/kotlin/**/*.kt", "*/kotlin/**/*.kts") })
            config.setFrom(listOfNotNull(sharedFile, override.takeIf { it.exists() }))
            baseline.set(project.file("detekt-baseline.xml"))
        }
        project.tasks.withType(Detekt::class.java).configureEach {
            exclude("**/build/**", "**/generated/**")
        }
        project.tasks.named("check") { dependsOn("detekt") }
        project.tasks.withType(AbstractTestTask::class.java).configureEach { dependsOn("detekt") }
    }

    private companion object {
        val SHARED: String = DetektConventionPlugin::class.java
            .getResourceAsStream("/ci/diffuser/detekt.yml")!!.bufferedReader().readText()
    }
}
