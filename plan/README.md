# kotlin-kit

No GitHub repository yet (local only).

Small Kotlin/Native libraries shared by several projects: TOML, YAML, semver, HTTP and SQLite.

## Decisions

- Extracted from rtok-kotlin (`libs/toml`, `toml-ktoml`, `toml-aot`, `yaml-aot`, `semver`,
  `http`, `http-ktor`, `db`, `db-room` at rtok-kotlin `48d9c31`) for ketch-kotlin (ketch-kotlin
  T3). The packages `rtok.toml`, `rtok.http` and `rtok.db` were renamed to the neutral `toml`,
  `http` and `db`; `tomlaot`, `yamlaot` and `semver` kept their names.
- rtok-kotlin still builds its own copy; it moves to this package in a separate session.
- Consumers use a composite build (`includeBuild`) rather than a published artifact, as with
  kotlin-wasm.
- Native targets only (macOS arm64, Linux x64 and arm64), as in rtok-kotlin: the tests use native
  APIs. Windows waits for a consumer that needs it (ketch-kotlin T13; `:http-ktor` will need a
  WinHttp engine then).
- `.editorconfig` and the detekt baselines came with the code from rtok-kotlin; the disabled
  ktlint rules and baseline entries are debt to clean up by hand.
