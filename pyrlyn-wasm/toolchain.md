# Toolchain

| Program | How to install | Why here | Source |
| --- | --- | --- | --- |
| JDK 21 | mise | Gradle and the JVM target | https://openjdk.org |
| Gradle 9.7.1 | wrapper (`./gradlew`) | Build | https://github.com/gradle/gradle |
| Kotlin 2.4.21 | Gradle plugin | Compiler, Kotlin Multiplatform | https://github.com/JetBrains/kotlin |
| shared-settings | `packages/shared-settings` above the build (or `SHARED_SETTINGS_DIR`), local included build | Shared ktlint and detekt settings | https://github.com/pyrlyn/packages/tree/main/shared-settings |

## Maven

| Package | Where | Source | Why here |
| --- | --- | --- | --- |
| io.github.charlietap.chasm:chasm 2.3.0 | local (`:wasm-chasm`) | https://github.com/CharlieTap/chasm | The WebAssembly interpreter |
| org.jetbrains.kotlin:kotlin-test | local (tests) | https://github.com/JetBrains/kotlin | Tests |
| org.jlleitschuh.gradle.ktlint 14.2.0 (ktlint 1.8.0) | via shared-settings | https://github.com/JLLeitschuh/ktlint-gradle | Formatting lint |
| dev.detekt 2.0.0-alpha.6 | via shared-settings | https://github.com/detekt/detekt | Static analysis |
