// An in-memory Store, a fake clock and a race kit: what the engine's tests and every other Store's tests share.
configure<org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension> {
    sourceSets.commonMain.dependencies {
        api(project(":core"))
        api(kotlin("test"))
    }
}
