# packages

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

## Checks

CI (`.github/workflows/ci.yml`) runs `./gradlew check` in every package on macOS arm64; `mise.toml`
pins the JDK and the Conan that pyrlyn-kit needs.

## Dependency verification

Each package's `gradle/verification-metadata.xml` holds the SHA-256 of every file its build
downloads (plugins, libraries, Kotlin/Native, Node.js), so a changed or substituted artifact fails
the build. Gradle reads it only when the package is the root build: a consumer that includes a
package verifies with its own file. The checksums were written on macOS arm64; the Linux and
Windows variants of the host-specific downloads are trusted by name. After changing a dependency,
rewrite the file from that package's directory:

```sh
./gradlew --write-verification-metadata sha256 check
```

(`shared-settings` has no wrapper: `../pyrlyn-kit/gradlew -p . --write-verification-metadata sha256 build`.)
