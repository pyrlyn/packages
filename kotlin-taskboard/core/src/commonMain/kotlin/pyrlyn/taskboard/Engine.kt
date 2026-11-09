package pyrlyn.taskboard

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import kotlin.time.Duration
import kotlin.time.Instant

private const val MAX_KEY = 200

/** [Board] over any [Store]: all the rules live here, so every store and every front end gets them. */
internal class Engine(
    private val store: Store,
    private val options: BoardOptions,
) : Board {
    /** Whole seconds, the precision tasks keep on the wire, so what a write returns is what a read returns. */
    private fun now() = Instant.fromEpochSeconds(options.clock.now().epochSeconds)

    override fun snapshot(filter: Filter): BoardView {
        val r = store.snapshot()
        val now = now()
        val leases = State.decodeLeases(r.state(StateNames.LEASES))
        val tasks = r.tasks()
        val ready = Ready.select(tasks.map { it.value }) { isStale(it, leases[it.id], now, options) }.map { it.task.id }.toSet()
        val cards = tasks.map { card(it, leases[it.value.id], it.value.id in ready, now) }
        val shown = cards.filter { matches(filter, it, ready) }
        return BoardView(
            seq = IndexState.decode(r.state(StateNames.INDEX))?.seq ?: 0,
            columns = Status.entries.map { s -> Column(s, shown.filter { it.status == s }.sortedWith(compareBy<Card> { it.priority }.thenBy { it.id })) },
            warnings = r.warnings(),
        )
    }

    private fun card(
        v: Versioned<Task>,
        lease: Lease?,
        ready: Boolean,
        now: Instant,
    ): Card {
        val t = v.value
        return Card(
            id = t.id,
            title = t.title,
            status = t.status,
            priority = t.priority,
            parent = t.parent,
            assignee = t.assignee,
            blockedBy = t.blockedBy,
            conflictsWith = t.conflictsWith,
            parallel = t.parallel,
            ready = ready,
            stale = t.status.isActive && isStale(t, lease, now, options),
            lease = lease,
            rev = v.rev,
            remote = t.remote,
        )
    }

    private fun matches(
        f: Filter,
        c: Card,
        ready: Set<TaskId>,
    ): Boolean {
        val status = if (f.statuses.isNotEmpty()) c.status in f.statuses else f.all || c.status.isActive
        return status && (f.parent == null || c.parent == f.parent) &&
            (f.mine == null || c.assignee == f.mine || c.lease?.agent == f.mine) &&
            (!f.ready || c.id in ready)
    }

    override fun get(id: TaskId): Versioned<Task> = store.snapshot().tasks().firstOrNull { it.value.id == id } ?: throw BoardException(BoardError.NotFound(id.toString()))

    override fun show(id: TaskId): Shown {
        val r = store.snapshot()
        val tasks = r.tasks()
        val task = tasks.firstOrNull { it.value.id == id } ?: throw BoardException(BoardError.NotFound(id.toString()))
        val lease = State.decodeLeases(r.state(StateNames.LEASES))[id]
        return Shown(
            task = task,
            lease = lease,
            stale = task.value.status.isActive && isStale(task.value, lease, now(), options),
            subtasks = tasks.filter { it.value.parent == id }.map { it.value.id }.sorted(),
        )
    }

    override fun create(
        task: NewTask,
        write: Write,
    ) = single(Op.Create(task, null, write.ifRev, write.baseRev), write)

    override fun update(
        id: TaskId,
        patch: Patch,
        write: Write,
    ) = single(Op.Update(id, patch, write.ifRev, write.baseRev), write)

    override fun move(
        id: TaskId,
        status: Status,
        report: String?,
        force: Boolean,
        write: Write,
    ) = single(Op.Move(id, status, report, force, write.ifRev, write.baseRev), write)

    override fun claim(
        id: TaskId?,
        agent: String,
        lease: Duration?,
        write: Write,
    ): ClaimResult? {
        val written =
            try {
                single(Op.Claim(id, agent, lease, write.ifRev, write.baseRev), write)
            } catch (e: BoardException) {
                if (id == null && (e.error as? BoardError.NotFound)?.what == NOTHING_READY) return null
                throw e
            }
        val r = written.result
        return ClaimResult(r.task, r.lease ?: invalid("a claim without a lease"), r.changed, r.tookOverFrom, r.conflictsInProgress, written.seq, written.replayed)
    }

    override fun release(
        id: TaskId,
        agent: String,
        force: Boolean,
        write: Write,
    ) = single(Op.Release(id, agent, force, write.ifRev, write.baseRev), write)

    override fun batch(
        ops: List<Op>,
        write: Write,
    ): Written {
        if (write.ifRev != null || write.baseRev != null) invalid("a batch takes if_rev and base_rev per op")
        return run(ops, write, indexed = true)
    }

    private fun single(
        op: Op,
        write: Write,
    ) = run(listOf(op), write, indexed = false)

    private fun run(
        ops: List<Op>,
        write: Write,
        indexed: Boolean,
    ): Written {
        if (ops.isEmpty()) invalid("a batch needs at least one op")
        val key = write.key
        if (key != null && (key.isBlank() || key.length > MAX_KEY)) invalid("an idempotency key is 1 to $MAX_KEY characters")
        val hash = sha256Hex(Codec.requestText(ops))
        return store.locked(options.lockTimeout) { session ->
            val w = Work(session, options, now())
            w.reconcile()
            if (w.keys.entries.removeAll { w.now - it.value.at > options.keyTtl }) w.keysDirty = true
            val seen = key?.let { w.keys[it] }
            if (seen != null) {
                if (seen.hash != hash) throw BoardException(BoardError.KeyReused(key))
                w.commit()
                return@locked Codec.writtenFromJson(seen.result).copy(replayed = true)
            }
            val results =
                ops.mapIndexed { i, op ->
                    try {
                        w.apply(op, write.actor)
                    } catch (e: BoardException) {
                        throw if (indexed) BoardException(e.error, i) else e
                    }
                }
            val written = Written(results, w.index.seq)
            if (key != null) {
                w.keys[key] = KeyEntry(hash, w.now, Codec.writtenToJson(written))
                w.keysDirty = true
            }
            w.commit()
            written
        }
    }

    override fun changes(
        since: Long,
        limit: Int,
    ): ChangePage {
        if (limit < 1) invalid("limit must be at least 1")
        var r = store.snapshot()
        if (hasDrift(r)) {
            // Hand edits are journaled by whoever notices them first; a watcher counts, so a quiet board still reports them.
            store.locked(options.lockTimeout) { s -> Work(s, options, now()).also { it.reconcile() }.commit() }
            r = store.snapshot()
        }
        val index = IndexState.decode(r.state(StateNames.INDEX)) ?: IndexState()
        if (since < index.first - 1 || since > index.seq) return ChangePage(index.seq, emptyList(), reset = true)
        val entries =
            Work
                .parseJournal(r.state(StateNames.JOURNAL))
                .filter { it.seq > since }
                .sortedBy { it.seq }
                .take(limit)
        return ChangePage(index.seq, entries, reset = false)
    }

    private fun hasDrift(r: Reader): Boolean {
        val index = IndexState.decode(r.state(StateNames.INDEX)) ?: return true
        val tasks = r.tasks()
        return tasks.size != index.tasks.size || tasks.any { index.tasks[it.value.id]?.rev != it.rev }
    }

    override fun watch(
        since: Long,
        pollInterval: Duration,
    ): Flow<WatchEvent> =
        flow {
            var cursor = since
            while (true) {
                val page = withContext(Dispatchers.Default) { changes(cursor) }
                if (page.reset) {
                    emit(WatchEvent.Reset(page.seq))
                    cursor = page.seq
                    continue
                }
                for (change in page.changes) {
                    emit(WatchEvent.Changed(change))
                    cursor = change.seq
                }
                if (page.changes.isEmpty()) delay(pollInterval)
            }
        }
}
