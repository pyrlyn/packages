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

## `:files`: a board in a folder

`FileStore(dir)` keeps a board as Markdown files, in the layout rtok's board writes, so `Boards.open(FileStore(dir),
options)` reads and replaces it. Native targets only (it needs kotlin-kit's `fs` and `yaml-aot`).

```
<dir>/todo/R12-port-the-parser.md       one folder per status: todo, in-progress, done, closed
<dir>/.gitignore                        ".lock\n.state/\n.sync/\n", written only when missing
<dir>/.lock                             the lock file
<dir>/.state/ids.json, leases.json, keys.json, index.json, journal.jsonl, txn.json
```

- **A file** is `<ID>-<slug>.md` (the old `<ID> - <slug>.md` is read too): front matter `id`, `title`, `created`,
  `updated` (ISO 8601 UTC; unix seconds are read too, a missing time is the file's mtime), then only when set `parent`,
  `priority` (not when 2), `assignee`, `blocked_by`, `conflicts_with`, `parallel`, `remote`, then the task's unknown keys.
  The body is the description, then the closing report under a final `## Report` heading. There is no `status` key: the
  folder is the status, and a `status:` key in a file is ignored (with a warning when it disagrees).
  The id in the front matter is the truth; a file whose name says another id is skipped with a warning.
- **Limits of the format.** A description or report with a line that is exactly `## Report` cannot be told from the
  heading, so a write refuses it (`invalid`) before touching anything. Leading and trailing whitespace of the description
  and report is not kept. Unknown front matter keys are kept as values, rewritten in flow style (`labels: ["a","b"]`),
  so **comments inside them are lost** when the board rewrites the file; comments elsewhere in a file are lost as well,
  because the whole file is regenerated.
- **Reads take no lock and create nothing.** A missing folder is an empty board. Unknown folders, loose task files
  outside a column, unparseable files and mismatched names are skipped and listed in `warnings()`. Two files with one id
  (a hand copy, or a move a crash interrupted): the newer `updated` wins, a tie goes to the later column, and the next
  write by the board removes the other copy. The `rev` of a task hashes its column and its file bytes, so a hand move
  changes it.
- **Writes take the lock.** `FileStore.locked` holds an exclusive `flock` on `<dir>/.lock`, taken by every call on its own
  file, so threads and processes on one folder exclude each other (`busy` when it stays taken for the timeout). Network
  file systems are not supported.
- **A commit is all or nothing, across a crash.** It plans every file it will write or remove, writes the plan to
  `.state/txn.json` (fsynced), applies it (task files, then removals, then state blobs, then appended journal lines)
  and deletes the plan. If the process dies in between, the next lock holder replays the plan first; every step can be
  repeated, and an append counts from the length the blob had, so nothing is added twice. A reader that takes no lock can
  still see a commit half applied (files are replaced one by one, each atomically, never half written). A commit never
  overwrites or removes a file it could not read: it fails with `invalid` and names the file. A plan that does not
  parse or names a file outside the board is not replayed; the write fails with `invalid` and leaves it for you to check.

Failures are one `BoardException` with a stable `code`: `not_found`, `conflict`, `claimed`, `invalid`, `key_reused`,
`busy`, `remote`.
