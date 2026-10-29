# Toolchain

| Program | How to install | Why here | Source |
| --- | --- | --- | --- |
| JDK 21 | mise | Gradle | https://openjdk.org |
| Gradle 9.8.2 | wrapper (`./gradlew`) | Build | https://github.com/gradle/gradle |
| Kotlin 2.4.21 | Gradle plugin | Compiler, Kotlin Multiplatform and Kotlin/Native | https://github.com/JetBrains/kotlin |
| Buf CLI | Gradle (`build.buf:buf`, fetched by the kotlinx-rpc plugin) | Runs the protobuf code generation of `proto` | https://github.com/bufbuild/buf |
| shared-settings | `packages/shared-settings` above the build (or `SHARED_SETTINGS_DIR`), local included build | Shared settings, ktlint and detekt | https://github.com/pyrlyn/packages/tree/main/shared-settings |
| kotlin-kit | `packages/kotlin-kit` above the build (or `KOTLIN_KIT_DIR`), local included build | `pyrlyn.kit:http`, the HTTP interface device login talks through | https://github.com/pyrlyn/packages/tree/main/kotlin-kit |

## Maven

| Package | Where | Source | Why here |
| --- | --- | --- | --- |
| org.jetbrains.kotlinx:kotlinx-rpc-grpc-core, -grpc-server, -grpc-client, -protobuf 0.11.0-grpc-190 | local (`proto`; JetBrains `kxrpc-grpc` repository) | https://github.com/Kotlin/kotlinx-rpc | gRPC and protobuf messages on Kotlin/Native |
| org.jetbrains.kotlinx.rpc.plugin 0.11.0-grpc-190 | local (`proto`) | https://github.com/Kotlin/kotlinx-rpc | Generates the messages and services; its compiler plugin (the 2.4.20 build, the newest it publishes) |
| org.jetbrains.kotlinx:kotlinx-coroutines-core 1.11.0 | transitive (kotlin-kit `http`, kotlinx-rpc) | https://github.com/Kotlin/kotlinx.coroutines | The `suspend` API, the call mutex, timeouts |
| org.jetbrains.kotlinx:kotlinx-coroutines-test 1.11.0 | local (tests) | https://github.com/Kotlin/kotlinx.coroutines | Virtual time in the device login tests |
| org.jetbrains.kotlinx:kotlinx-serialization-json 1.12.0 | local (`client`) | https://github.com/Kotlin/kotlinx.serialization | The device login's JSON and the token's claims |
| io.kotest:kotest-assertions-core 6.2.6 | local (tests) | https://github.com/kotest/kotest | Assertions |

## Vendored files

| Files | Where | Source | Why here |
| --- | --- | --- | --- |
| `pyrlyn/**/*.proto`, `buf.yaml` | `proto/contracts` (copied unchanged from `../backend/proto`) | https://github.com/pyrlyn/backend | The sync, usage and product contracts |
| `grpc/health/v1/health.proto` | `proto/health` | https://github.com/grpc/grpc-proto | The standard health service |
| `google/rpc/status.proto`, `error_details.proto` | `proto/googleapis` (googleapis commit 646596e286b464ca2c2cca6f4c767e7c19046489) | https://github.com/googleapis/googleapis | The gRPC rich error model the server sends its reasons in |
