# AGENTS.md — kotlin-kit

If an `AGENTS.md` or `CLAUDE.md` exists higher in the tree, follow it too; on conflict, ask the creator.

## What this project is

Small Kotlin Multiplatform libraries shared by several projects: files, TOML, YAML, semver, an HTTP
client, SQLite and child processes. Each concern is an engine-neutral interface plus, where a third-party library
does the work, one adapter module:

- `:fs`, crash-safe file writes and tree helpers over POSIX and Win32 (no third-party library).
- `:toml` (interface, `TomlWriter` and the strict `TomlParser`, checked against `toml/testdata/toml-test`) and `:toml-ktoml` (ktoml); `:toml-aot`, a comment-keeping TOML editor.
- `:yaml-aot`, a YAML reader and scalar writer.
- `:semver`, version ordering.
- `:http` (interface) and `:http-ktor` (Ktor client).
- `:db` (interface), `:db-room` (androidx.sqlite, bundled SQLite; macOS and Linux) and `:db-native` (SQLite's C API
  from Conan; Windows); `:db-testing` holds the checks both adapters' tests run.
- `:process`, child processes under a deadline over `posix_spawn` or CreateProcessW and a job object (no third-party
  library).

Consumers program against the interfaces, so swapping an engine never touches them.

## How it is used

Nothing is published. A consumer includes this build (`includeBuild`) and depends on
`pyrlyn.kit:<module>`; Gradle substitutes the projects.

## Rules

- Code, docs, comments and commits in English.
- `./gradlew check` must pass: tests on the host's native target, ktlint and detekt.
- Every target a consumer builds stays declared: macOS arm64, Linux x64 and arm64, Windows x64. A module with no
  Windows (or no POSIX) side still declares the target and compiles to an empty klib there.
- Windows tests run on a Windows machine before a change to `mingwMain` lands.
- Only adapter modules see third-party types. A new third-party dependency needs the creator's
  approval.
- Input from files, the network and other programs is untrusted: parsers fail with their own
  exception, never a crash.
