// A Store on a folder of Markdown files, one folder per column. Native only: the lock and the crash-safe writes
// come from kotlin-kit's fs module, which has no JVM or JS side.
configure<org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension> {
    sourceSets {
        nativeMain.dependencies {
            api(project(":core"))
            implementation("pyrlyn.kit:fs")
            implementation("pyrlyn.kit:yaml-aot")
        }
        nativeTest.dependencies { implementation(project(":testing")) }
    }
}
