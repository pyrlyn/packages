// Adapter: SQLite's own C API behind the db interfaces, for Windows, where androidx.sqlite (db-room) has no build.
// Conan builds a static sqlite3 from source with K/N's mingw toolchain (gradle/conan.gradle.kts) and cinterop links it
// into the klib. The other targets compile to an empty klib, so a consumer can depend on both adapters everywhere.
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.plugin.getKotlinPluginVersion
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget

extra["conanTargets"] = setOf("mingwX64")
extra["kotlinVersion"] = getKotlinPluginVersion(logger)
apply(from = rootProject.file("gradle/conan.gradle.kts"))

configure<KotlinMultiplatformExtension> {
    sourceSets.commonMain.dependencies { api(project(":db")) }
    sourceSets.mingwTest.dependencies { implementation(project(":db-testing")) }
    targets.withType<KotlinNativeTarget>().matching { it.name == "mingwX64" }.configureEach {
        val root =
            layout.buildDirectory
                .dir("conan/mingwX64/direct_deploy/sqlite3")
                .get()
                .asFile
        val cinterop =
            compilations.getByName("main").cinterops.create("sqlite") {
                definitionFile.set(project.file("src/nativeInterop/cinterop/sqlite.def"))
                includeDirs(root.resolve("include"))
                extraOpts("-libraryPath", root.resolve("lib").path, "-staticLibrary", "libsqlite3.a")
            }
        tasks.named(cinterop.interopProcessingTaskName) { dependsOn("conanInstallMingwX64") }
    }
}
