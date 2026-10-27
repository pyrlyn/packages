# Agents

- Generic only: no consumer (rtok) types or mapping here; consumers own their mapping and tests.
- Platform-free logic goes in `graph` commonMain with JVM tests; wasmJs code stays thin.
- Gate: `CI_DIFFUSER_DIR=~/GitHub/listepo/apps/ci-diffuser ./gradlew check` must pass. English code, docs and commits.
