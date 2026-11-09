# packages

Shared Kotlin packages and Gradle build settings for the pyrlyn tools (Octo, rtok and others).
Each directory is its own Gradle build; consumers include it as a Gradle included build from a
checkout of this repository, found in a `packages/` directory above their own build.

| Package | What it is |
| --- | --- |
| [kotlin-adwaita](kotlin-adwaita) | Adwaita-style UI components for Compose Multiplatform, without business logic |
| [kotlin-fluent](kotlin-fluent) | Fluent UI components for Compose Multiplatform, without business logic |
| [kotlin-glass](kotlin-glass) | Glass UI components for Compose Multiplatform, without business logic |
| [kotlin-kit](kotlin-kit) | Small Kotlin/Native libraries: command lines, files, archives, TOML, YAML, semver, HTTP, SQLite, child processes |
| [kotlin-macos](kotlin-macos) | macOS-style UI components for Compose Multiplatform, without business logic |
| [kotlin-snapshot](kotlin-snapshot) | Snapshot assertions for Kotlin Multiplatform |
| [kotlin-sync](kotlin-sync) | Sync contracts and the Kotlin client for the cloud sync |
| [kotlin-taskboard](kotlin-taskboard) | The task board engine: model, store SPI and a board with an API for parallel clients that never conflict |
| [kotlin-wasm](kotlin-wasm) | An engine-neutral WebAssembly host interface with a Chasm adapter |
| [kotlin-webgl](kotlin-webgl) | Reusable WebGL2 rendering for Kotlin/Wasm |
| [shared-settings](shared-settings) | Shared Gradle build settings: ktlint, detekt, Kotlin/Native, repositories, build cache |

Each package keeps its own README, AGENTS.md and plan.
