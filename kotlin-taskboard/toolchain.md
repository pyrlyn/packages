# Toolchain

| Program | How to install | Why here | Source |
| --- | --- | --- | --- |
| JDK 21 | mise | Gradle | https://openjdk.org |
| Gradle 9.8.2 | wrapper (`./gradlew`) | Build | https://github.com/gradle/gradle |
| Kotlin 2.4.21 | Gradle plugin | Compiler, Kotlin Multiplatform and Kotlin/Native | https://github.com/JetBrains/kotlin |
| shared-settings | `packages/shared-settings` above the build (or `SHARED_SETTINGS_DIR`), local included build | Shared settings, ktlint and detekt | https://github.com/pyrlyn/packages/tree/main/shared-settings |

## Maven

| Package | Where | Source | Why here |
| --- | --- | --- | --- |
| org.jetbrains.kotlinx:kotlinx-serialization-json 1.12.0 | local (`:core`) | https://github.com/Kotlin/kotlinx.serialization | The JSON codec and the JSON trees stores keep |
| org.jetbrains.kotlinx:kotlinx-coroutines-core 1.11.0 | local (`:core`, `:testing`) | https://github.com/Kotlin/kotlinx.coroutines | `Flow` for `watch`; the race kit and `MemoryStore`'s lock |
| org.kotlincrypto.hash:sha2 0.8.0 | local (`:core`) | https://github.com/KotlinCrypto/hash | SHA-256 for revs and idempotency-key request hashes |
| org.jetbrains.kotlin:kotlin-test | local (tests, `:testing`) | https://github.com/JetBrains/kotlin | Tests |
| org.jlleitschuh.gradle.ktlint 14.2.0 (ktlint 1.8.0) | via shared-settings | https://github.com/JLLeitschuh/ktlint-gradle | Formatting lint |
| dev.detekt 2.0.0-alpha.6 | via shared-settings | https://github.com/detekt/detekt | Static analysis |
