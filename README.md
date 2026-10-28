# kotlin-kit

Small Kotlin/Native libraries shared by several projects: command lines, status-line icons, files, archives, TOML, YAML, semver, HTTP, SQLite and child processes.
They started as rtok-kotlin's `libs/` and are shared so that projects read and write the same
formats the same way.

## Modules

| Module | Package | What |
| --- | --- | --- |
| `pyrlyn.kit:cli` | `cli` | `command`, `Command`, `Arg`, `ValueKind`, `PossibleValue`, `parse`, `Parsed`, `Matches`, `Settings`, `help`, `completions`, `CompletionShell`, `valueOptions`, `manPages`, `ManPage`, `ManHeader`: a declarative command tree read with clap 4.6's semantics, whose help, usage and errors match a clap program's byte for byte, the bash, elvish, fish, PowerShell and zsh scripts clap_complete writes for it, and clap_mangen's man pages; on native, `terminalSettings` and `Parsed.Exit.write` read the terminal as clap does |
| `pyrlyn.kit:fs` | `fs` | `WriteOptions`, `atomic`, `ifChanged`, `textOrEmpty`, `copyTree`, `removeAny`, `createNew`, `hardLink`, `symlink`, `createDir`, `setMode`, `removeFile`, `removeEmptyDir`, `rename`, `modifiedAgo`, `setTimes`, `FsException`: crash-safe file writes and tree helpers over POSIX and Win32 (port of Rust fs-kit; on Windows `canon` drops the `\\?\` prefix where dunce would) |
| `pyrlyn.kit:toml` | `toml` | `TomlReader`, `TomlDocument`, `TomlValues`, `TomlException`: TOML into kotlinx-serialization JSON trees; `TomlParser`: a strict TOML 1.1 reader that passes all of toml-test; `TomlWriter`: JSON trees into TOML text exactly as Rust `toml` 1.1 writes it |
| `pyrlyn.kit:toml-ktoml` | `toml.ktoml` | `KtomlReader` over [ktoml](https://github.com/orchestr7/ktoml) |
| `pyrlyn.kit:toml-aot` | `tomlaot` | `TomlDoc`: a TOML editor that keeps comments and layout |
| `pyrlyn.kit:yaml-aot` | `yamlaot` | `Yaml.parse` into `YamlValue`, `YamlText` scalar writing |
| `pyrlyn.kit:semver` | `semver` | `VersionKey`: version ordering |
| `pyrlyn.kit:icons` | `icons` | `icon`, `iconIn`, `OPERATION_ICONS`, `ICON_WIDTH`, `padding`, `gutter`, `displayWidth`: icons for what a status-line verb is doing and the fixed-width gutter they sit in (port of Rust operation-icons), with the terminal column count unicode-width gives a string |
| `pyrlyn.kit:http` | `http` | `HttpClient`, `HttpRequest`, `HttpResponse`, `HttpSettings`, `HttpException`; `HttpClient.stream` with `HttpHead` and `HttpBody` for a GET whose body is read as it arrives |
| `pyrlyn.kit:http-ktor` | `http.ktor` | `KtorHttp` over the [Ktor](https://github.com/ktorio/ktor) client (Darwin, Curl, WinHttp) |
| `pyrlyn.kit:db` | `db` | `Db`, `DbDriver`, `Row`, `Cursor`, `DbException`: a driver-neutral SQL interface |
| `pyrlyn.kit:db-room` | `db.room` | `RoomDbDriver` over androidx.sqlite with bundled SQLite (macOS and Linux) |
| `pyrlyn.kit:db-native` | `db.native` | `NativeDbDriver` over SQLite's C API, linked statically from Conan (Windows, where androidx.sqlite has no build) |
| `pyrlyn.kit:db-testing` | `db.testing` | `DriverContract`: the checks every `Db` adapter's tests run |
| `pyrlyn.kit:db-sqldelight` | `db.sqldelight` | `KitSqlDriver`: [SQLDelight](https://github.com/sqldelight/sqldelight)'s generated queries on a `Db` |
| `pyrlyn.kit:archive` | `archive` | `gzipSource`, `bzip2Source`, `xzSource`, `deflateSource` (and `gzip`, `bzip2`, `deflate`, `crc32`): streaming codecs as kotlinx-io sources; `TarReader`, `TarWriter`, `ZipReader`, `ZipWriter`; `ArchiveException`. Reads archives as tar-rs and Rust zip do, with every length an archive states checked; zlib from the platform, bzip2 and liblzma linked statically from Conan |
| `pyrlyn.kit:process` | `process` | `Command.runBounded`, `Output`, `ExitStatus`, `ProcessException`: a child with no stdin, capped output and a deadline that kills its whole tree (a process group from `posix_spawn` on POSIX, a job object on Windows, with the command line and program lookup Rust's std uses there); `Command.status` for a child that shares the terminal; `currentExe` |
| `pyrlyn.kit:loopback` | `loopback` | `LoopbackServer`: an HTTP/1.1 server on 127.0.0.1 for tests, written over POSIX sockets or winsock so a reply can break framing (a lying `Content-Length`, a cut-off body) |

## Use

```kotlin
// settings.gradle.kts
includeBuild(System.getenv("KOTLIN_KIT_DIR") ?: "../../packages/kotlin-kit")

// build.gradle.kts
dependencies { implementation("pyrlyn.kit:toml-aot") }
```

Targets: macOS arm64, Linux x64 and arm64, Windows x64 (mingw). POSIX code sits in `unixMain`, its Windows
counterpart in `mingwMain`; Win32 calls whose error code matters go through small C shims that read `GetLastError`
before the Kotlin runtime can overwrite it.

## Build

`./gradlew check` runs the tests, ktlint and detekt. The lint settings come from a checkout of
ci-diffuser at `../../apps/ci-diffuser`, or `CI_DIFFUSER_DIR`.

`:archive` and `:db-native` need `conan` on `PATH`: their builds run `conan install` against the module's
`conan.lock` with the profiles in `conan/` (`gradle/conan.gradle.kts`), into `<module>/build/conan/<target>`. macOS
and Linux take ConanCenter's prebuilt static bzip2 and liblzma; ConanCenter has only MSVC binaries for Windows, so
bzip2, liblzma and sqlite3 are built there from the locked recipes with Kotlin/Native's own clang and mingw sysroot
(CMake from `PATH`). ConanCenter has no Linux arm64 binaries, so `linuxArm64` compiles against the headers and links
nothing yet.

Windows tests run on a Windows machine: copy `<module>/build/bin/mingwX64/debugTest/test.exe` there and run it
(`:toml` wants `TOML_TEST_DIR` pointing at `toml/testdata/toml-test`).
