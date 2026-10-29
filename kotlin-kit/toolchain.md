# Toolchain

| Program | How to install | Why here | Source |
| --- | --- | --- | --- |
| JDK 21 | mise | Gradle | https://openjdk.org |
| Gradle 9.8.2 | wrapper (`./gradlew`) | Build | https://github.com/gradle/gradle |
| Kotlin 2.4.21 | Gradle plugin | Compiler, Kotlin Multiplatform and Kotlin/Native | https://github.com/JetBrains/kotlin |
| conan 2.33.0 | mise | Fetches (macOS, Linux) or builds (Windows) the static C libraries `:archive` and `:db-native` link | https://github.com/conan-io/conan |
| CMake | system package | Conan's Windows builds of bzip2, xz_utils and sqlite3 | https://cmake.org |
| Ninja | system package | CMake's generator for those builds on a Windows host, which has no make | https://github.com/ninja-build/ninja |
| zlib | Kotlin/Native platform library (`platform.zlib`) | gzip, deflate and CRC-32 in `:archive` | https://zlib.net |
| shared-settings | `packages/shared-settings` above the build (or `SHARED_SETTINGS_DIR`), local included build | Shared settings, ktlint and detekt | `packages/shared-settings` (no remote) |

## Maven

| Package | Where | Source | Why here |
| --- | --- | --- | --- |
| org.jetbrains.kotlinx:kotlinx-io-core 0.9.1 | local (`:fs`, `:archive`; `:toml` tests) | https://github.com/Kotlin/kotlinx-io | `Path` in the file API; reading the toml-test files |
| org.jetbrains.kotlinx:kotlinx-serialization-json 1.12.0 | local (`:toml`) | https://github.com/Kotlin/kotlinx.serialization | TOML values as JSON trees |
| com.akuleshov7:ktoml-core 0.7.1 | local (`:toml-ktoml`) | https://github.com/orchestr7/ktoml | TOML parser and writer |
| org.jetbrains.kotlinx:kotlinx-coroutines-core 1.11.0 | local (`:http`) | https://github.com/Kotlin/kotlinx.coroutines | The `suspend` HTTP API |
| io.ktor:ktor-client-core, -darwin, -curl, -winhttp 3.6.0 | local (`:http-ktor`) | https://github.com/ktorio/ktor | HTTP client and its native engines |
| io.ktor:ktor-client-mock 3.6.0 | local (`:http-ktor` tests) | https://github.com/ktorio/ktor | HTTP tests without a network |
| androidx.sqlite:sqlite-bundled 2.7.1 | local (`:db-room`, macOS and Linux) | https://developer.android.com/jetpack/androidx/releases/sqlite | SQLite driver with SQLite bundled; it has no mingw build (checked 2.7.1 and 2.8.0-alpha03, 2026-10-28) |
| org.jetbrains.kotlin:kotlin-test | local (tests, `:db-testing`) | https://github.com/JetBrains/kotlin | Tests |
| org.jlleitschuh.gradle.ktlint 14.2.0 (ktlint 1.8.0) | via ci-diffuser | https://github.com/JLLeitschuh/ktlint-gradle | Formatting lint |
| dev.detekt 2.0.0-alpha.6 | via ci-diffuser | https://github.com/detekt/detekt | Static analysis |

## Conan (ConanCenter)

| Package | Where | Source | Why here |
| --- | --- | --- | --- |
| bzip2 1.0.8 | local (`:archive`, `archive/conan.lock`) | https://sourceware.org/bzip2/ | bzip2 codec, linked statically |
| xz_utils 5.8.4 | local (`:archive`, `archive/conan.lock`) | https://github.com/tukaani-project/xz | liblzma: the xz decoder, linked statically |
| sqlite3 3.54.0 | local (`:db-native`, `db-native/conan.lock`; Windows only) | https://sqlite.org | SQLite for Windows, built with FTS5 and linked statically |
