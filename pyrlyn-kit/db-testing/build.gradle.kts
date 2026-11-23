// Test helper: the behaviour every db adapter must have, as checks each adapter's own tests call with its driver.
configure<org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension> {
    sourceSets.commonMain.dependencies {
        api(project(":db"))
        implementation(kotlin("test"))
    }
}
