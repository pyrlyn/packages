package pyrlyn.sync.testing

import io.kotest.matchers.shouldBe
import kotlin.test.Test

// Ported from ../backend/crates/sync-client/src/testing.rs.

class MemTest {
    @Test
    fun theViewShowsUnpushedEditsOverSyncedRecords() {
        val mem = Mem()
        mem.edit("note", "a", "a1", 1)
        mem.edit("note", "a", "a2", 2)
        mem.edit("note", "b", "b", 3)
        mem.edit("note", "b", null, 4)
        mem.queued() shouldBe listOf("a", "b")
        mem.view() shouldBe mapOf("note/a" to "a2")
    }
}
