package ci.diffuser

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.tasks.testing.AbstractTestTask
import org.jlleitschuh.gradle.ktlint.KtlintExtension

/**
 * ktlint for one Kotlin module (org.jlleitschuh.gradle.ktlint).
 * Tasks: `ktlintCheck` (also wired into `check`) and `ktlintFormat`.
 * Every test task (K/N test runs, jvmTest, allTests, any AbstractTestTask) depends on `ktlintCheck`,
 * so style errors fail fast before tests, locally and in CI alike. Generated and build dirs are excluded.
 */
class KtlintConventionPlugin : Plugin<Project> {
    override fun apply(project: Project) {
        project.pluginManager.apply("org.jlleitschuh.gradle.ktlint")
        project.extensions.configure(KtlintExtension::class.java) {
            version.set(Versions.KTLINT)
            filter {
                exclude { it.file.path.contains("/build/") || it.file.path.contains("/generated/") }
            }
        }
        project.tasks.withType(AbstractTestTask::class.java).configureEach { dependsOn("ktlintCheck") }
    }
}
