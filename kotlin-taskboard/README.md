# kotlin-taskboard

The engine of rtok's task board: a Kotlin Multiplatform library whose `Board` lets many clients (threads, processes,
agents) read and write one board in parallel without losing each other's work.

- **Revisions.** Every task read carries a `rev`, a short hash of what the store holds for it. `Write(ifRev = rev)` is a
  strict compare-and-set; `Write(baseRev = rev)` merges: it applies unless a field it sets changed since that revision.
  Without either, a patch applies field by field.
- **Atomic operations.** `create`, `update`, `move`, `claim`, `release` each run under one lock hold. `claim(null, agent)`
  picks the next ready task and claims it in the same hold.
- **Batches.** `batch(ops)` validates and applies all operations or none; steps refer to earlier creates by `Ref`.
- **Idempotency keys.** A retried write with the same `key` returns the first result (`replayed = true`).
- **Leases.** A claim is a lease with a TTL; claiming again renews it, an expired lease can be taken over, and a
  `liveness` hook keeps a lease whose holder is still running.
- **Journal.** Every change is journaled; `changes(since)` and `watch(since)` feed clients, and hand edits of the store
  are noticed and journaled as `external`.

Nothing is published. Include the build from a checkout of [pyrlyn/packages](https://github.com/pyrlyn/packages) found
in a `packages/` directory above yours, as `kotlin-kit` is:

```kotlin
// settings.gradle.kts, in pluginManagement { } next to the other sharedBuild() calls
includeBuild(sharedBuild("kotlin-taskboard", "KOTLIN_TASKBOARD_DIR"))
```

```kotlin
// build.gradle.kts
sourceSets.commonMain.dependencies { implementation("pyrlyn.taskboard:core") }
sourceSets.commonTest.dependencies { implementation("pyrlyn.taskboard:testing") }
```

```kotlin
val board = Boards.open(store, BoardOptions(idPrefix = "R"))

val a = board.create(NewTask("Port the parser"), Write(key = "req-42")).task      // Versioned<Task>: task + rev
board.update(a.value.id, Patch(title = "Port the TOML parser"), Write(baseRev = a.rev))
val mine = board.claim(null, agent = "a1")                                         // next ready task, or null
board.move(a.value.id, Status.Done, report = "ported", write = Write(actor = "a1"))
board.batch(listOf(Op.Create(NewTask("parent"), ref = "p"), Op.Create(NewTask("child", parent = Ref("p")))))
val page = board.changes(since = 0)                                                // ChangePage(seq, changes, reset)
```

A `Store` only reads, locks and writes; the engine owns every rule. `MemoryStore` in `:testing` is a real in-memory
store (its lock is a real mutex, so threads race for real) and `Race.run` starts N clients at once.

Failures are one `BoardException` with a stable `code`: `not_found`, `conflict`, `claimed`, `invalid`, `key_reused`,
`busy`, `remote`.
