---
status: in progress
priority: P1
complexity: 5
readiness: 60%
agent: Claude Code / claude-opus-5-5
---

# T1. Board engine with a concurrency API

Why: rtok T8.8 moves the task board into a library whose API lets parallel clients (CLI, MCP, daemon, web, other
programs) work on one board without conflicts. This task is the engine and its test kit; the file store, JSON-RPC,
sync and trackers follow (see `roadmap.md`).

Done means: `:core` holds the model, the JSON codec, the `Store` SPI and the engine behind `Board` (revisions with
`if_rev`, field merge with `base_rev`, atomic claim-next, all-or-nothing batches, idempotency keys, leases with a
liveness hook, the journal with `changes` and `watch`, hand-edit detection, ids that are never reused); `:testing` holds
`MemoryStore`, `FakeClock` and the `Race` kit; tests show that N clients racing on one board never lose a write, never
claim a task twice, never leave a half-applied batch or a partial commit, and never duplicate on a retried key.

Check: `./gradlew check` in `kotlin-taskboard/` green.

## Execution plan

1. Scaffold the build like `kotlin-snapshot` and `kotlin-kit` on `shared-settings`.
2. `:core`: model, codec, SPI, engine.
3. `:testing`: store, clock, race kit; engine tests for every rule.
