// Adapter: SQLDelight's generated queries run on any kit `db` connection, so SQLite stays the bundled one of db-room.
configure<org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension> {
    sourceSets.commonMain.dependencies {
        api(project(":db"))
        api("app.cash.sqldelight:runtime:2.4.1")
    }
    sourceSets.nativeTest.dependencies { implementation(project(":db-room")) }
}
