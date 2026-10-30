# packages

Shared Kotlin packages and Gradle build settings for the pyrlyn tools (Octo, rtok and others).
Each directory is its own Gradle build; consumers include it as a Gradle included build from a
checkout of this repository, found in a `packages/` directory above their own build.

| Package | What it is |
| --- | --- |
| [kotlin-glass](kotlin-glass) | Glass UI components for Compose Multiplatform, without business logic |
| [kotlin-kit](kotlin-kit) | Small Kotlin/Native libraries: command lines, files, archives, TOML, YAML, semver, HTTP, SQLite, child processes |
| [kotlin-snapshot](kotlin-snapshot) | Snapshot assertions for Kotlin Multiplatform |
| [kotlin-sync](kotlin-sync) | Sync contracts and the Kotlin client for the cloud sync |
| [kotlin-wasm](kotlin-wasm) | An engine-neutral WebAssembly host interface with a Chasm adapter |
| [kotlin-webgl](kotlin-webgl) | Reusable WebGL2 rendering for Kotlin/Wasm |
| [shared-settings](shared-settings) | Shared Gradle build settings: ktlint, detekt, Kotlin/Native, repositories, build cache |

Each package keeps its own README, AGENTS.md and plan.
