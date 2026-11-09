# AGENTS.md — kotlin-taskboard

If an `AGENTS.md` or `CLAUDE.md` exists higher in the tree, follow it too; on conflict, ask the creator.

## What this project is

The engine of rtok's task board, as a Kotlin Multiplatform library: the task model, the `Store` SPI, and the `Board`
that implements revisions, field merges, atomic claim-next, all-or-nothing batches, idempotency keys, claim leases,
a change journal and `watch` over any store, so clients that work in parallel never conflict. Modules:

| Module | Holds |
| --- | --- |
| `:core` | `Task`, `Status`, `TaskId`, `Rev`, `Patch`, `Op`, `Write`, `BoardError`, the JSON codec (one wire schema for the CLI, MCP and JSON-RPC), the `Store` SPI and the engine |
| `:testing` | `MemoryStore` (real lock, fault injection), `FakeClock`, the `Race` kit and `Invariants` |

Later modules (`files`, `sync`, `rpc`, the trackers) are listed in `roadmap.md`. See README.md.

## Rules

- Code, docs, comments and commits in English. Comments say why, never what.
- `./gradlew check` must pass: tests on the host's native target, ktlint and detekt.
- Every target a consumer builds stays declared: macOS arm64, Linux x64 and arm64, Windows x64.
- The package has no rtok imports and no platform code in `:core`; a store does the I/O.
- Every rule of the engine has a test that runs against `MemoryStore`; a rule about concurrency has a test that races
  real threads (`Race`), not one that calls the board twice in a row.
- Input from files, JSON and other programs is untrusted: it fails with `BoardException(invalid)`, never a crash.
- A new third-party dependency needs the creator's approval; versions follow the other packages and are not raised
  without it.
- Nothing is published. A consumer includes this build with `sharedBuild("kotlin-taskboard", "KOTLIN_TASKBOARD_DIR")`.
