# Agents

- Generic only: no consumer (rtok) types or mapping here; consumers own their mapping and tests.
- Platform-free logic goes in `graph` commonMain with JVM tests; wasmJs code stays thin.
- Gate: `./gradlew check` must pass (shared-settings is found in any ancestor `packages/`, or `SHARED_SETTINGS_DIR`). English code, docs and commits.
