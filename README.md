<h1><picture><source media="(prefers-color-scheme: dark)" srcset="brand/logo-dark.svg"><img src="brand/logo.svg" width="32" height="32" alt="" align="absmiddle"></picture> packages</h1>

Shared Kotlin packages and Gradle build settings for the pyrlyn tools (Octo, rtok and others).
Each directory is its own Gradle build; consumers include it as a Gradle included build from a
checkout of this repository, found in a `packages/` directory above their own build.

| Package | What it is |
| --- | --- |
| [pyrlyn-adwaita](pyrlyn-adwaita) | Adwaita-style UI components for Compose Multiplatform, without business logic |
| [pyrlyn-fluent](pyrlyn-fluent) | Fluent UI components for Compose Multiplatform, without business logic |
| [pyrlyn-glass](pyrlyn-glass) | Glass UI components for Compose Multiplatform, without business logic |
| [pyrlyn-kit](pyrlyn-kit) | Small Kotlin/Native libraries: command lines, files, archives, TOML, YAML, semver, HTTP, SQLite, child processes |
| [pyrlyn-macos](pyrlyn-macos) | macOS-style UI components for Compose Multiplatform, without business logic |
| [pyrlyn-snapshot](pyrlyn-snapshot) | Snapshot assertions for Kotlin Multiplatform |
| [pyrlyn-sync](pyrlyn-sync) | Sync contracts and the Kotlin client for the cloud sync |
| [pyrlyn-taskboard](pyrlyn-taskboard) | The task board engine: model, store SPI and a board with an API for parallel clients that never conflict |
| [pyrlyn-wasm](pyrlyn-wasm) | An engine-neutral WebAssembly host interface with a Chasm adapter |
| [pyrlyn-webgl](pyrlyn-webgl) | Reusable WebGL2 rendering for Kotlin/Wasm |
| [shared-settings](shared-settings) | Shared Gradle build settings: ktlint, detekt, Kotlin/Native, repositories, build cache |

Each package keeps its own README, AGENTS.md and plan.
