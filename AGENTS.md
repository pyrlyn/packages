# AGENTS.md — kotlin-wasm

If an `AGENTS.md` or `CLAUDE.md` exists higher in the tree, follow it too; on conflict, ask the creator.

## What this project is

A small Kotlin Multiplatform WebAssembly host layer shared by several projects:

- `:wasm` is an engine-neutral interface: values, memory, host functions, instances and the
  runtime. It has no dependencies.
- `:wasm-chasm` is the one adapter, over the Chasm interpreter.

Hosts program against `:wasm` only, so swapping the engine never touches them.

## How it is used

Nothing is published. A consumer includes this build (`includeBuild`) and depends on
`pyrlyn.wasm:wasm` or `pyrlyn.wasm:wasm-chasm`; Gradle substitutes the projects.

## Rules

- Code, docs, comments and commits in English.
- Every change goes through a PR. Do not push `main` directly and never force-push.
- `./gradlew check` must pass: tests on the JVM and the host's native target, ktlint and detekt.
- Every target a consumer builds stays declared: JVM, macOS arm64, Linux x64 and arm64, Windows
  x64.
- Only `:wasm-chasm` sees Chasm types. A new third-party dependency needs the creator's approval.
- Guest data is untrusted: every read from guest memory is bounds-checked.
