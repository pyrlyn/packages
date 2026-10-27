// TOML reading abstraction: the only TOML API consumers depend on, with its own strict TomlParser
// (another adapter in :toml-ktoml).
configure<org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension> {
    sourceSets.commonMain.dependencies { api("org.jetbrains.kotlinx:kotlinx-serialization-json:1.12.0") }
    sourceSets.nativeTest.dependencies { implementation("org.jetbrains.kotlinx:kotlinx-io-core:0.9.1") }
}

// The official toml-test files, read by TomlTestSuiteTest.
val suite = layout.projectDirectory.dir("testdata/toml-test")
tasks.withType<org.jetbrains.kotlin.gradle.targets.native.tasks.KotlinNativeTest>().configureEach {
    inputs.dir(suite)
    environment("TOML_TEST_DIR", suite.asFile.path)
}
