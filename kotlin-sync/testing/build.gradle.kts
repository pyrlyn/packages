// An app's side of sync in memory, for this package's tests, the sync service's client tests and
// apps' own tests; apps never ship it.
kotlin {
    sourceSets.commonMain.dependencies { api(project(":client")) }
    sourceSets.commonTest.dependencies { implementation(libs.kotest.assertions.core) }
}
