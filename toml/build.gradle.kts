// TOML reading abstraction: the only TOML API consumers depend on (adapter in :toml-ktoml).
configure<org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension> { sourceSets.commonMain.dependencies { api("org.jetbrains.kotlinx:kotlinx-serialization-json:1.12.0") } }
