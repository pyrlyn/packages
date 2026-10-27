# kotlin-kit

Small Kotlin/Native libraries shared by several projects: files, archives, TOML, YAML, semver, HTTP, SQLite and child processes.
They started as rtok-kotlin's `libs/` and are shared so that projects read and write the same
formats the same way.

## Modules

| Module | Package | What |
| --- | --- | --- |
| `pyrlyn.kit:fs` | `fs` | `WriteOptions`, `atomic`, `ifChanged`, `textOrEmpty`, `copyTree`, `removeAny`, `createNew`, `hardLink`, `symlink`, `createDir`, `setMode`, `removeFile`, `modifiedAgo`, `setTimes`, `FsException`: crash-safe file writes and tree helpers over POSIX (port of Rust fs-kit) |
| `pyrlyn.kit:toml` | `toml` | `TomlReader`, `TomlDocument`, `TomlValues`, `TomlException`: TOML into kotlinx-serialization JSON trees; `TomlParser`: a strict TOML 1.1 reader that passes all of toml-test; `TomlWriter`: JSON trees into TOML text exactly as Rust `toml` 1.1 writes it |
| `pyrlyn.kit:toml-ktoml` | `toml.ktoml` | `KtomlReader` over [ktoml](https://github.com/orchestr7/ktoml) |
| `pyrlyn.kit:toml-aot` | `tomlaot` | `TomlDoc`: a TOML editor that keeps comments and layout |
| `pyrlyn.kit:yaml-aot` | `yamlaot` | `Yaml.parse` into `YamlValue`, `YamlText` scalar writing |
| `pyrlyn.kit:semver` | `semver` | `VersionKey`: version ordering |
| `pyrlyn.kit:http` | `http` | `HttpClient`, `HttpRequest`, `HttpResponse`, `HttpSettings`, `HttpException`; `HttpClient.stream` with `HttpHead` and `HttpBody` for a GET whose body is read as it arrives |
| `pyrlyn.kit:http-ktor` | `http.ktor` | `KtorHttp` over the [Ktor](https://github.com/ktorio/ktor) client (Darwin, Curl) |
| `pyrlyn.kit:db` | `db` | `Db`, `DbDriver`, `Row`, `DbException`: a driver-neutral SQL interface |
| `pyrlyn.kit:db-room` | `db.room` | `RoomDbDriver` over androidx.sqlite with bundled SQLite |
| `pyrlyn.kit:archive` | `archive` | `gzipSource`, `bzip2Source`, `xzSource`, `deflateSource` (and `gzip`, `bzip2`, `deflate`, `crc32`): streaming codecs as kotlinx-io sources; `TarReader`, `TarWriter`, `ZipReader`, `ZipWriter`; `ArchiveException`. Reads archives as tar-rs and Rust zip do, with every length an archive states checked; zlib from the platform, bzip2 and liblzma linked statically from Conan |
| `pyrlyn.kit:process` | `process` | `Command.runBounded`, `Output`, `ExitStatus`, `ProcessException`: a child with no stdin, capped output and a deadline that kills its process group (POSIX `posix_spawn`) |
| `pyrlyn.kit:loopback` | `loopback` | `LoopbackServer`: an HTTP/1.1 server on 127.0.0.1 for tests, written over sockets so a reply can break framing (a lying `Content-Length`, a cut-off body) |

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

`:archive` needs `conan` on `PATH`: its build runs `conan install` against `archive/conan.lock`, which takes
ConanCenter's prebuilt static bzip2 and liblzma once (Conan caches them) into `archive/build/conan/<target>`.
ConanCenter has no Linux arm64 binaries, so `linuxArm64` compiles against the headers and links nothing yet.
