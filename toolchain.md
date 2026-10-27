# Toolchain

| Program | How to install | Why here | Source |
| --- | --- | --- | --- |
| JDK 21 | mise | Gradle | https://openjdk.org |
| Gradle 9.8.2 | wrapper (`./gradlew`) | Build | https://github.com/gradle/gradle |
| Kotlin 2.4.21 | Gradle plugin | Compiler, Kotlin Multiplatform and Kotlin/Native | https://github.com/JetBrains/kotlin |
| ci-diffuser | checkout at `../../apps/ci-diffuser` (or `CI_DIFFUSER_DIR`), local included build | Shared settings, ktlint and detekt | `apps/ci-diffuser` (no remote) |

## Maven

| Package | Where | Source | Why here |
| --- | --- | --- | --- |
| org.jetbrains.kotlinx:kotlinx-io-core 0.9.1 | local (`:fs`; `:toml` tests) | https://github.com/Kotlin/kotlinx-io | `Path` in the file API; reading the toml-test files |
| org.jetbrains.kotlinx:kotlinx-serialization-json 1.12.0 | local (`:toml`) | https://github.com/Kotlin/kotlinx.serialization | TOML values as JSON trees |
| com.akuleshov7:ktoml-core 0.7.1 | local (`:toml-ktoml`) | https://github.com/orchestr7/ktoml | TOML parser and writer |
| org.jetbrains.kotlinx:kotlinx-coroutines-core 1.11.0 | local (`:http`) | https://github.com/Kotlin/kotlinx.coroutines | The `suspend` HTTP API |
| io.ktor:ktor-client-core, -darwin, -curl 3.6.0 | local (`:http-ktor`) | https://github.com/ktorio/ktor | HTTP client and its native engines |
| io.ktor:ktor-client-mock 3.6.0 | local (`:http-ktor` tests) | https://github.com/ktorio/ktor | HTTP tests without a network |
| androidx.sqlite:sqlite-bundled 2.7.1 | local (`:db-room`) | https://developer.android.com/jetpack/androidx/releases/sqlite | SQLite driver with SQLite bundled |
| org.jetbrains.kotlin:kotlin-test | local (tests) | https://github.com/JetBrains/kotlin | Tests |
| org.jlleitschuh.gradle.ktlint 14.2.0 (ktlint 1.8.0) | via ci-diffuser | https://github.com/JLLeitschuh/ktlint-gradle | Formatting lint |
| dev.detekt 2.0.0-alpha.6 | via ci-diffuser | https://github.com/detekt/detekt | Static analysis |
