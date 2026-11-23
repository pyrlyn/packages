package pyrlyn.taskboard

/** Which tasks can be claimed, and which dependency edges form a cycle. Ported from rtok's `Ready`. */
internal object Ready {
    class Item(
        val task: Task,
        val stale: Boolean,
        val conflictsInProgress: List<TaskId>,
    )

    /** [stale] says whether [Task.assignee]'s hold on that task can be taken; only asked for a held task. */
    fun select(
        tasks: Collection<Task>,
        stale: (Task) -> Boolean,
    ): List<Item> {
        val anyBlocks = tasks.any { it.blockedBy.isNotEmpty() }
        val active = tasks.filter { it.status.isActive }.map { it.id }.toSet()
        val parents = tasks.filter { it.status.isActive }.mapNotNull { it.parent }.toSet()
        return tasks
            .mapNotNull { task ->
                val held = !task.assignee.isNullOrEmpty()
                val isStale = held && stale(task)
                val claimable =
                    when (task.status) {
                        Status.Todo -> !held || isStale
                        Status.InProgress -> isStale
                        Status.Done, Status.Closed -> false
                    }
                when {
                    !claimable -> null
                    task.blockedBy.any { it in active } -> null
                    !anyBlocks && task.id in parents -> null
                    else -> Item(task, isStale, conflictingInProgress(task, tasks))
                }
            }.sortedWith(compareBy<Item> { it.task.priority }.thenBy { it.task.id })
    }

    private fun conflictingInProgress(
        task: Task,
        tasks: Collection<Task>,
    ) = tasks
        .filter { it.status == Status.InProgress && it.id != task.id }
        .filter { task.id in it.conflictsWith || it.id in task.conflictsWith }
        .map { it.id }
        .sorted()

    /** The first active id in `blocked_by`. */
    fun activeBlocker(
        task: Task,
        tasks: Collection<Task>,
    ): TaskId? {
        val active = tasks.filter { it.status.isActive }.map { it.id }.toSet()
        return task.blockedBy.firstOrNull { it in active }
    }

    /** Cycles in the wait-graph (task to blocker, parent to child), each rotated to its lowest id. */
    fun findCycles(tasks: Collection<Task>): List<List<TaskId>> {
        val adj = mutableMapOf<TaskId, MutableSet<TaskId>>()

        fun node(id: TaskId) = adj.getOrPut(id) { mutableSetOf() }
        for (task in tasks) {
            node(task.id)
            for (b in task.blockedBy) {
                node(task.id).add(b)
                node(b)
            }
            task.parent?.let {
                node(it).add(task.id)
                node(task.id)
            }
        }
        val color = mutableMapOf<TaskId, Int>()
        val stack = ArrayList<TaskId>()
        val cycles = ArrayList<List<TaskId>>()

        fun dfs(n: TaskId) {
            color[n] = 1
            stack.add(n)
            for (to in adj[n].orEmpty().sorted()) {
                when (color[to] ?: 0) {
                    1 -> stack.indexOf(to).takeIf { it >= 0 }?.let { cycles.add(rotateLowest(stack.subList(it, stack.size).toList())) }
                    0 -> dfs(to)
                }
            }
            stack.removeAt(stack.size - 1)
            color[n] = 2
        }
        for (n in adj.keys.sorted()) if ((color[n] ?: 0) == 0) dfs(n)
        return cycles.distinct().sortedWith(::compareLists)
    }

    private fun compareLists(
        a: List<TaskId>,
        b: List<TaskId>,
    ): Int {
        for (i in 0 until minOf(a.size, b.size)) a[i].compareTo(b[i]).let { if (it != 0) return it }
        return a.size.compareTo(b.size)
    }

    private fun rotateLowest(cycle: List<TaskId>): List<TaskId> {
        val c = if (cycle.size >= 2 && cycle.first() == cycle.last()) cycle.dropLast(1) else cycle
        if (c.isEmpty()) return c
        val at = c.indices.minBy { c[it] }
        return c.drop(at) + c.take(at)
    }
}

/** The agent a task is held by: the lease's holder, else a bare `assignee` (a hand edit). */
internal fun holderOf(
    task: Task,
    lease: Lease?,
): String? = (lease?.agent ?: task.assignee)?.takeIf { it.isNotEmpty() }

/** The hold can be taken: its lease ran out (or never existed) and the host does not vouch for the holder. */
internal fun isStale(
    task: Task,
    lease: Lease?,
    now: kotlin.time.Instant,
    options: BoardOptions,
): Boolean {
    val holder = holderOf(task, lease) ?: return false
    if (lease != null && lease.expires > now) return false
    // The hook is wrapped per call (Diag.guard), so a failure already arrived here as "no answer".
    return options.liveness?.invoke(holder) != true
}
