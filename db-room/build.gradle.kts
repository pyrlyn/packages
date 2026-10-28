// Adapter: androidx.sqlite (Room KMP's driver layer, bundled SQLite) behind the db interfaces, on macOS and Linux.
// androidx.sqlite has no Windows build: there the mingw klib is empty and db-native stands in.
configure<org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension> {
    sourceSets.commonMain.dependencies { api(project(":db")) }
    sourceSets.unixMain.dependencies { implementation("androidx.sqlite:sqlite-bundled:2.7.1") }
    sourceSets.unixTest.dependencies { implementation(project(":db-testing")) }
}
