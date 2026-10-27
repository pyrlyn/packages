# AGENTS.md — kotlin-snapshot

If an `AGENTS.md` or `CLAUDE.md` exists higher in the tree, follow it too; on conflict, ask the creator.
## What this project is

insta-style snapshot assertions for Kotlin Multiplatform (`:snapshot`), reading and writing insta's
`.snap` format. See README.md.

## Rules

- Code, docs, comments and commits in English.
- Every change goes through a PR. Do not push `main` directly and never force-push.
- `./gradlew check` must pass: tests on the JVM and the host's native target, ktlint and detekt.
- Every target a consumer builds stays declared: JVM, macOS arm64, Linux x64 and arm64, Windows x64.
- No third-party dependencies without the creator's approval.
