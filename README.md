# kotlin-kit

Small Kotlin/Native libraries shared by several projects: files, TOML, YAML, semver, HTTP and SQLite.
They started as rtok-kotlin's `libs/` and are shared so that projects read and write the same
formats the same way.

## Modules

| Module | Package | What |
| --- | --- | --- |
| `pyrlyn.kit:fs` | `fs` | `WriteOptions`, `atomic`, `ifChanged`, `textOrEmpty`, `copyTree`, `removeAny`, `FsException`: crash-safe file writes and tree helpers over POSIX (port of Rust fs-kit) |
| `pyrlyn.kit:toml` | `toml` | `TomlReader`, `TomlDocument`, `TomlValues`, `TomlException`: TOML into kotlinx-serialization JSON trees; `TomlWriter`: JSON trees into TOML text exactly as Rust `toml` 1.1 writes it |
| `pyrlyn.kit:toml-ktoml` | `toml.ktoml` | `KtomlReader` over [ktoml](https://github.com/orchestr7/ktoml) |
| `pyrlyn.kit:toml-aot` | `tomlaot` | `TomlDoc`: a TOML editor that keeps comments and layout |
| `pyrlyn.kit:yaml-aot` | `yamlaot` | `Yaml.parse` into `YamlValue`, `YamlText` scalar writing |
| `pyrlyn.kit:semver` | `semver` | `VersionKey`: version ordering |
| `pyrlyn.kit:http` | `http` | `HttpClient`, `HttpRequest`, `HttpResponse`, `HttpSettings`, `HttpException` |
| `pyrlyn.kit:http-ktor` | `http.ktor` | `KtorHttp` over the [Ktor](https://github.com/ktorio/ktor) client (Darwin, Curl) |
| `pyrlyn.kit:db` | `db` | `Db`, `DbDriver`, `Row`, `DbException`: a driver-neutral SQL interface |
| `pyrlyn.kit:db-room` | `db.room` | `RoomDbDriver` over androidx.sqlite with bundled SQLite |

## Use

```kotlin
// settings.gradle.kts
includeBuild(System.getenv("KOTLIN_KIT_DIR") ?: "../../packages/kotlin-kit")

// build.gradle.kts
dependencies { implementation("pyrlyn.kit:toml-aot") }
```

Targets: macOS arm64, Linux x64 and arm64.

## Build

`./gradlew check` runs the tests, ktlint and detekt. The lint settings come from a checkout of
ci-diffuser at `../../apps/ci-diffuser`, or `CI_DIFFUSER_DIR`.
