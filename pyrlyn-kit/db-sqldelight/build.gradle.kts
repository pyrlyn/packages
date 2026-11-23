// Adapter: SQLDelight's generated queries run on any kit `db` connection, so SQLite stays the one db-room bundles
// (db-native's on Windows).
configure<org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension> {
    sourceSets.commonMain.dependencies {
        api(project(":db"))
        api("app.cash.sqldelight:runtime:2.4.1")
    }
    sourceSets.unixTest.dependencies { implementation(project(":db-room")) }
    sourceSets.mingwTest.dependencies { implementation(project(":db-native")) }
}
