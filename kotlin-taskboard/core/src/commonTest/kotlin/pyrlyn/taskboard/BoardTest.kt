package pyrlyn.taskboard

import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes

class BoardTest {
    private val f = Fixture()
    private val board get() = f.board

    @Test
    fun createGivesIdsUnderTheConfiguredPrefix() {
        assertEquals(id("T1"), f.create("a").value.id)
        assertEquals(id("T2"), f.create("b").value.id)
        val child = board.create(NewTask("c", parent = id("T1"))).task.value
        assertEquals(id("T1.1"), child.id)
        assertEquals(id("T1"), child.parent)
        assertEquals(
            id("T1.2"),
            board
                .create(NewTask("d", parent = id("T1")))
                .task.value.id,
        )
        assertEquals(Status.Todo, child.status)
    }

    @Test
    fun idsSeedFromTheBoardAndNeverRepeat() {
        f.store.handEdit(Task(id("T7"), "old", created = f.clock.now(), updated = f.clock.now()))
        assertEquals(id("T8"), f.create().value.id)
        f.store.handRemove(id("T8"))
        assertEquals(id("T9"), f.create().value.id)
    }

    @Test
    fun createRejectsBadInput() {
        assertCode("invalid") { board.create(NewTask("  ")) }
        assertCode("invalid") { board.create(NewTask("a\nb")) }
        assertCode("invalid") { board.create(NewTask("a", priority = 5)) }
        assertCode("not_found") { board.create(NewTask("a", parent = id("T9"))) }
        f.create("a")
        val deep =
            board
                .create(NewTask("b", parent = id("T1")))
                .task.value.id
        val deeper =
            board
                .create(NewTask("c", parent = deep))
                .task.value.id
        assertCode("invalid") { board.create(NewTask("d", parent = deeper)) }
    }

    @Test
    fun getAndShow() {
        val a = f.create("a")
        board.create(NewTask("sub", parent = a.value.id))
        assertEquals(a, board.get(a.value.id))
        assertEquals(listOf(id("T1.1")), board.show(a.value.id).subtasks)
        assertCode("not_found") { board.get(id("T9")) }
    }

    @Test
    fun updateChangesOnlyTheFieldsItSets() {
        val a = f.create("a")
        f.clock.advance(5.minutes)
        val b = board.update(a.value.id, Patch(description = "why")).task
        assertEquals("a", b.value.title)
        assertEquals("why", b.value.description)
        assertEquals(f.clock.now(), b.value.updated)
        assertEquals(a.value.created, b.value.created)
        val c = board.update(a.value.id, Patch(title = "A", priority = 0, parallel = true)).task.value
        assertEquals(listOf("A", "why", 0, true), listOf(c.title, c.description, c.priority, c.parallel))
    }

    @Test
    fun aNoOpWriteChangesNothing() {
        val a = f.create("a")
        val seq = board.snapshot().seq
        f.clock.advance(5.minutes)
        val r = board.update(a.value.id, Patch(title = "a", priority = 2, addBlockedBy = emptySet()))
        assertFalse(r.result.changed)
        assertEquals(a, r.task)
        assertEquals(seq, board.snapshot().seq)
        assertEquals(a, board.get(a.value.id))
        val m = board.move(a.value.id, Status.Todo)
        assertFalse(m.result.changed)
        assertEquals(seq, board.changes(0).seq)
    }

    @Test
    fun setFieldsTakeAddAndRemove() {
        val a = f.create("a")
        val b = f.create("b")
        val c = f.create("c")
        val r = board.update(a.value.id, Patch(addBlockedBy = setOf(b.value.id, c.value.id), addConflictsWith = setOf(b.value.id))).task.value
        assertEquals(listOf(b.value.id, c.value.id), r.blockedBy)
        assertEquals(listOf(b.value.id), r.conflictsWith)
        val d = board.update(a.value.id, Patch(removeBlockedBy = setOf(b.value.id), removeConflictsWith = setOf(b.value.id))).task.value
        assertEquals(listOf(c.value.id), d.blockedBy)
        assertTrue(d.conflictsWith.isEmpty())
    }

    @Test
    fun dependencyCyclesAndSelfEdgesAreRefused() {
        val a = f.create("a").value.id
        val b = f.create("b").value.id
        assertCode("invalid") { board.update(a, Patch(addBlockedBy = setOf(a))) }
        assertCode("invalid") { board.update(a, Patch(addConflictsWith = setOf(a))) }
        board.update(a, Patch(addBlockedBy = setOf(b)))
        assertCode("invalid") { board.update(b, Patch(addBlockedBy = setOf(a))) }
        assertCode("invalid") { board.update(a, Patch(parent = a)) }
        val child =
            board
                .create(NewTask("child", parent = a))
                .task.value.id
        assertCode("invalid") { board.update(a, Patch(parent = child)) }
    }

    @Test
    fun parentCanBeSetAndCleared() {
        val a = f.create("a").value.id
        val b = f.create("b").value.id
        assertEquals(
            a,
            board
                .update(b, Patch(parent = a))
                .task.value.parent,
        )
        assertNull(
            board
                .update(b, Patch(clearParent = true))
                .task.value.parent,
        )
        assertCode("not_found") { board.update(b, Patch(parent = id("T9"))) }
    }

    @Test
    fun remoteLinkIsSetAndUnlinked() {
        val a = f.create("a").value.id
        assertEquals(
            "https://x/1",
            board
                .update(a, Patch(remote = "https://x/1"))
                .task.value.remote,
        )
        assertNull(
            board
                .update(a, Patch(remote = ""))
                .task.value.remote,
        )
    }

    @Test
    fun moveSetsReportAndGuardsOpenSubtasks() {
        val a = f.create("a").value.id
        val sub =
            board
                .create(NewTask("sub", parent = a))
                .task.value.id
        assertCode("invalid") { board.move(a, Status.Done) }
        assertCode("invalid") { board.move(sub, Status.InProgress, report = "no") }
        val forced = board.move(a, Status.Done, report = "forced", force = true).task.value
        assertEquals("forced", forced.report)
        board.move(sub, Status.Closed, report = "dropped")
        val back = board.move(a, Status.Todo).task.value
        assertNull(back.report)
        assertEquals(Status.Todo, back.status)
    }

    @Test
    fun readyFollowsBlockersPriorityAndOrder() {
        val a = f.create("a").value.id
        val b = f.create("b") { it.copy(priority = 0) }.value.id
        val c = f.create("c") { it.copy(blockedBy = listOf(a)) }.value.id

        fun ready() =
            board
                .snapshot()
                .columns
                .first { it.status == Status.Todo }
                .cards
                .filter { it.ready }
                .map { it.id }
        assertEquals(listOf(b, a), ready())
        board.move(a, Status.Done)
        assertEquals(listOf(b, c), ready())
        assertEquals(
            b,
            board
                .claim(null, "x")!!
                .task.value.id,
        )
    }

    @Test
    fun snapshotFiltersAndSortsColumns() {
        val a = f.create("a").value.id
        val b = f.create("b") { it.copy(priority = 0) }.value.id
        board.claim(a, "me")
        board.move(b, Status.Done)
        val v = board.snapshot()
        assertEquals(Status.entries, v.columns.map { it.status })
        assertEquals(listOf(a), v.columns[1].cards.map { it.id })
        assertTrue(v.columns[2].cards.isEmpty())
        assertEquals(
            listOf(b),
            board
                .snapshot(Filter(all = true))
                .columns[2]
                .cards
                .map { it.id },
        )
        assertEquals(
            listOf(b),
            board
                .snapshot(Filter(statuses = setOf(Status.Done)))
                .columns[2]
                .cards
                .map { it.id },
        )
        assertEquals(
            listOf(a),
            board
                .snapshot(Filter(mine = "me"))
                .columns
                .flatMap { it.cards }
                .map { it.id },
        )
        assertTrue(board.snapshot(Filter(mine = "other")).columns.all { it.cards.isEmpty() })
        assertEquals(
            listOf(a),
            board
                .snapshot(Filter(parent = null))
                .columns[1]
                .cards
                .map { it.id },
        )
    }

    @Test
    fun cardCarriesLeaseAndRev() {
        val a = f.create("a").value.id
        val claim = board.claim(a, "me")!!
        val card =
            board
                .snapshot()
                .columns[1]
                .cards
                .single()
        assertEquals(claim.lease, card.lease)
        assertEquals(claim.task.rev, card.rev)
        assertFalse(card.ready)
        assertFalse(card.stale)
    }

    @Test
    fun revChangesWithContent() {
        val a = f.create("a")
        val b = board.update(a.value.id, Patch(title = "b")).task
        assertNotEquals(a.rev, b.rev)
        assertEquals(b.rev, board.get(a.value.id).rev)
    }

    @Test
    fun warningsComeFromTheStore() {
        f.store.warnings = listOf("skipped todo/bad.md")
        assertEquals(listOf("skipped todo/bad.md"), board.snapshot().warnings)
    }

    @Test
    fun fieldDiffsUseNullForNotSet() {
        val a = f.create("a").value.id
        board.update(a, Patch(description = "d"))
        val last = board.changes(0).changes.last()
        assertEquals(mapOf("description" to FieldChange(JsonNull, JsonPrimitive("d"))), last.fields)
    }
}
