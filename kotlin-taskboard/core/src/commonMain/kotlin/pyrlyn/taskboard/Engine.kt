package pyrlyn.taskboard

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
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

    /** The options of one call: the liveness hook cannot throw and reports through [diag]. */
    private fun scoped(diag: Diag) = options.copy(liveness = diag.guard(options.liveness))

    private fun readLeases(
        r: Reader,
        diag: Diag,
    ) = diag.recover(StateNames.LEASES, { mutableMapOf() }) { State.decodeLeases(r.state(StateNames.LEASES)) }

    private fun readIndex(
        r: Reader,
        diag: Diag,
    ) = diag.recover(StateNames.INDEX, { IndexState.recovered(r.state(StateNames.JOURNAL)) }) { IndexState.decode(r.state(StateNames.INDEX)) ?: IndexState() }

    override fun snapshot(filter: Filter): BoardView {
        val diag = Diag(options.warn)
        val opts = scoped(diag)
        val r = store.snapshot()
        val now = now()
        val leases = readLeases(r, diag)
        val tasks = r.tasks()
        val ready = Ready.select(tasks.map { it.value }) { isStale(it, leases[it.id], now, opts) }.map { it.task.id }.toSet()
        val cards = tasks.map { card(it, leases[it.value.id], it.value.id in ready, now, opts) }
        val shown = cards.filter { matches(filter, it, ready) }
        return BoardView(
            seq = readIndex(r, diag).seq,
            columns = Status.entries.map { s -> Column(s, shown.filter { it.status == s }.sortedWith(compareBy<Card> { it.priority }.thenBy { it.id })) },
            warnings = r.warnings() + diag.messages,
        )
    }

    private fun card(
        v: Versioned<Task>,
        lease: Lease?,
        ready: Boolean,
        now: Instant,
        opts: BoardOptions,
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
            stale = t.status.isActive && isStale(t, lease, now, opts),
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
        val diag = Diag(options.warn)
        val r = store.snapshot()
        val tasks = r.tasks()
        val task = tasks.firstOrNull { it.value.id == id } ?: throw BoardException(BoardError.NotFound(id.toString()))
        val lease = readLeases(r, diag)[id]
        return Shown(
            task = task,
            lease = lease,
            stale = task.value.status.isActive && isStale(task.value, lease, now(), scoped(diag)),
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
        val diag = Diag(options.warn)
        return store.locked(options.lockTimeout) { session ->
            val w = Work(session, scoped(diag), now(), diag)
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
            val diag = Diag(options.warn)
            store.locked(options.lockTimeout) { s -> Work(s, scoped(diag), now(), diag).also { it.reconcile() }.commit() }
            r = store.snapshot()
        }
        val index = readIndex(r, Diag(options.warn))
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
        val text = r.state(StateNames.INDEX)
        val tasks = r.tasks()
        // A board nobody wrote yet has nothing to reconcile; taking the lock for it would create the board in a file store.
        if (text == null && tasks.isEmpty()) return false
        val index = IndexState.decodeOrNull(text) ?: return true
        return tasks.size != index.tasks.size || tasks.any { index.tasks[it.value.id]?.rev != it.rev }
    }

    override fun watch(
        since: Long,
        pollInterval: Duration,
    ): Flow<WatchEvent> =
        flow {
            var cursor = since
            while (true) {
                val page = withContext(Dispatchers.IO) { changes(cursor) }
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
