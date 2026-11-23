package pyrlyn.taskboard

import kotlin.time.Instant

/** The text of the `not_found` that `claim(null)` raises when nothing is ready; the single-op API turns it into null. */
internal const val NOTHING_READY = "ready to claim"

private const val MAX_TITLE = 500

internal fun Work.apply(
    op: Op,
    actor: String?,
): OpResult =
    when (op) {
        is Op.Create -> create(op, actor)
        is Op.Update -> update(op, actor)
        is Op.Move -> move(op, actor)
        is Op.Claim -> claim(op, actor)
        is Op.Release -> release(op, actor)
    }

private fun Work.resolve(target: Target): TaskId =
    when (target) {
        is TaskId -> target.also { if (it !in tasks) throw BoardException(BoardError.NotFound(it.toString())) }
        is Ref -> refs[target.name] ?: invalid("unknown ref \"${target.name}\": a ref names a Create earlier in the same batch")
    }

private fun cleanTitle(raw: String): String {
    val title = raw.trim()
    if (title.isEmpty()) invalid("a task needs a title")
    if ('\n' in title || '\r' in title) invalid("a title is one line")
    if (title.length > MAX_TITLE) invalid("a title is at most $MAX_TITLE characters")
    return title
}

private fun checkPriority(level: Int) {
    if (level !in 0..PRIORITY_MAX) invalid("priority $level is outside 0-$PRIORITY_MAX")
}

/** The next free number under [parent] (null: top level), past every id the board holds and every one it ever gave out. */
private fun Work.allocate(parent: TaskId?): TaskId {
    val key = parent?.toString().orEmpty()
    val depth = parent?.path?.size ?: 0
    val taken =
        tasks.keys
            .filter { it.path.size == depth + 1 && (parent == null || (it.prefix == parent.prefix && it.path.take(depth) == parent.path)) }
            .maxOfOrNull { it.path[depth] }
            ?: 0u
    val next = maxOf(counters[key] ?: 0L, taken.toLong()) + 1
    if (next > UInt.MAX_VALUE.toLong()) invalid("no task numbers left under ${parent ?: "the top level"}")
    counters[key] = next
    countersDirty = true
    return parent?.child(next.toUInt()) ?: TaskId.top(options.idPrefix, next.toUInt())
}

private fun Work.create(
    op: Op.Create,
    actor: String?,
): OpResult {
    if (op.ifRev != null || op.baseRev != null) invalid("a create takes no if_rev or base_rev")
    val n = op.task
    val title = cleanTitle(n.title)
    checkPriority(n.priority)
    val parent = n.parent?.let(::resolve)
    val id = allocate(parent)
    op.ref?.let {
        if (it.isBlank() || it in refs) invalid("ref \"$it\" is empty or used twice")
        refs[it] = id
    }
    val task =
        Task(
            id = id,
            title = title,
            description = n.description,
            priority = n.priority,
            parent = parent,
            blockedBy = n.blockedBy.distinct().sorted(),
            conflictsWith = n.conflictsWith.distinct().sorted(),
            parallel = n.parallel,
            remote = n.remote?.takeIf { it.isNotEmpty() },
            created = now,
            updated = now,
        ).also { noSelfEdges(it) }
    save(null, task, "create", actor)
    return OpResult(versioned(id), changed = true)
}

private fun noSelfEdges(task: Task) {
    if (task.id in task.blockedBy) invalid("${task.id} cannot depend on itself")
    if (task.id in task.conflictsWith) invalid("${task.id} cannot conflict with itself")
}

private fun edit(
    list: List<TaskId>,
    add: Set<TaskId>,
    remove: Set<TaskId>,
) = if (add.isEmpty() && remove.isEmpty()) list else ((list + add).distinct() - remove).sorted()

private fun Work.update(
    op: Op.Update,
    actor: String?,
): OpResult {
    val id = resolve(op.target)
    val cur = versioned(id)
    val t = cur.value
    val p = op.patch
    p.priority?.let(::checkPriority)
    val parent =
        when {
            p.clearParent -> {
                null
            }

            p.parent != null -> {
                p.parent.also {
                    if (it == id) {
                        invalid("$id cannot be its own parent")
                    } else if (it !in tasks) {
                        throw BoardException(BoardError.NotFound(it.toString()))
                    }
                }
            }

            else -> {
                t.parent
            }
        }
    val after =
        t.copy(
            title = p.title?.let(::cleanTitle) ?: t.title,
            description = p.description ?: t.description,
            priority = p.priority ?: t.priority,
            parent = parent,
            parallel = p.parallel ?: t.parallel,
            remote = if (p.remote != null) p.remote.takeIf { it.isNotEmpty() } else t.remote,
            blockedBy = edit(t.blockedBy, p.addBlockedBy, p.removeBlockedBy),
            conflictsWith = edit(t.conflictsWith, p.addConflictsWith, p.removeConflictsWith),
        )
    noSelfEdges(after)
    check(cur, after, op.ifRev, op.baseRev)
    if (after.sameContent(t)) return OpResult(cur, changed = false)
    if (after.blockedBy != t.blockedBy || after.parent != t.parent) noNewCycle(t, after)
    save(t, after.copy(updated = now), "update", actor)
    return OpResult(versioned(id), changed = true)
}

private fun Work.noNewCycle(
    before: Task,
    after: Task,
) {
    val old = Ready.findCycles(tasks.values).toSet()
    val graph = tasks.values.map { if (it.id == after.id) after else it }
    val fresh = Ready.findCycles(graph).firstOrNull { before.id in it && it !in old }
    if (fresh != null) invalid("${before.id} would cycle: ${(fresh + fresh.first()).joinToString(" -> ")}")
}

private fun Work.claimedBy(
    task: Task,
    holder: String,
) = BoardException(BoardError.Claimed(task.id, holder, leases[task.id]?.expires))

private fun Work.move(
    op: Op.Move,
    actor: String?,
): OpResult {
    val id = resolve(op.target)
    val cur = versioned(id)
    val t = cur.value
    if (op.report != null && op.status.isActive) invalid("a report goes with the status done or closed")
    if (!op.status.isActive && !op.force) {
        val open =
            tasks.values
                .filter { it.parent == id && it.status.isActive }
                .map { it.id }
                .sorted()
        if (open.isNotEmpty()) invalid("$id has open subtasks: ${open.joinToString(", ")}; finish them first or pass force")
    }
    val holder = holder(t)
    // A live hold protects the task from everyone else; a write with no actor is not the holder.
    if (holder != null && holder != actor && !op.force && !stale(t)) throw claimedBy(t, holder)
    val after =
        t.copy(
            status = op.status,
            report = op.report ?: if (op.status.isActive) null else t.report,
            assignee = if (op.status == Status.Todo) null else t.assignee,
        )
    check(cur, after, op.ifRev, op.baseRev)
    if (after.sameContent(t)) return OpResult(cur, changed = false)
    save(t, after.copy(updated = now), "move", actor)
    if (op.status != Status.InProgress) setLease(id, null)
    return OpResult(versioned(id), changed = true)
}

private fun Work.leaseFor(
    id: TaskId,
    agent: String,
    length: kotlin.time.Duration?,
    since: Instant,
): Lease {
    val span = length ?: options.lease
    if (!span.isPositiveFinite) invalid("a lease must be positive")
    return Lease(agent, since, now + span).also { setLease(id, it) }
}

private fun Work.claim(
    op: Op.Claim,
    actor: String?,
): OpResult {
    if (op.agent.isBlank()) invalid("a claim needs an agent")
    val id =
        if (op.target != null) {
            resolve(op.target)
        } else {
            if (op.ifRev != null || op.baseRev != null) invalid("a claim of the next ready task takes no if_rev or base_rev")
            Ready
                .select(tasks.values) { stale(it) }
                .firstOrNull()
                ?.task
                ?.id ?: throw BoardException(BoardError.NotFound(NOTHING_READY))
        }
    val cur = versioned(id)
    val t = cur.value
    val holder = holder(t)
    if (holder == op.agent && t.status == Status.InProgress) {
        check(cur, t, op.ifRev, op.baseRev)
        val lease = leaseFor(id, op.agent, op.lease, leases[id]?.acquired ?: now)
        return OpResult(cur, changed = false, lease = lease)
    }
    if (!t.status.isActive) invalid("$id is ${t.status} and cannot be claimed")
    Ready.activeBlocker(t, tasks.values)?.let { invalid("$id is blocked by $it") }
    if (holder != null && holder != op.agent && !stale(t)) throw claimedBy(t, holder)
    val after = t.copy(assignee = op.agent, status = Status.InProgress)
    check(cur, after, op.ifRev, op.baseRev)
    save(t, after.copy(updated = now), "claim", actor ?: op.agent)
    val lease = leaseFor(id, op.agent, op.lease, now)
    val conflicts =
        tasks.values
            .filter { it.status == Status.InProgress && it.id != id && (id in it.conflictsWith || it.id in t.conflictsWith) }
            .map { it.id }
            .sorted()
    return OpResult(versioned(id), changed = true, lease = lease, tookOverFrom = holder?.takeIf { it != op.agent }, conflictsInProgress = conflicts)
}

private fun Work.release(
    op: Op.Release,
    actor: String?,
): OpResult {
    val id = resolve(op.target)
    val cur = versioned(id)
    val t = cur.value
    if (!t.status.isActive) invalid("$id is ${t.status} and cannot be released")
    val holder = holder(t)
    if (holder != null && holder != op.agent && !op.force && !stale(t)) throw claimedBy(t, holder)
    val after = t.copy(assignee = null, status = if (t.status == Status.InProgress) Status.Todo else t.status)
    check(cur, after, op.ifRev, op.baseRev)
    setLease(id, null)
    if (after.sameContent(t)) return OpResult(cur, changed = false)
    save(t, after.copy(updated = now), "release", actor ?: op.agent)
    return OpResult(versioned(id), changed = true)
}
