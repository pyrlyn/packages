package pyrlyn.taskboard

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ModelTest {
    @Test
    fun taskIdsParseAndPrint() {
        assertEquals("R12", id("r12").toString())
        assertEquals("AT1.2.3", id("AT1.2.3").toString())
        assertEquals(id("R5"), id("R5.1").parent())
        assertNull(id("R5").parent())
        assertEquals(id("R5.1"), id("R5").child(1u))
        assertTrue(id("R2") < id("R10"))
        assertTrue(id("R5") < id("R5.1"))
    }

    @Test
    fun badTaskIdsAreInvalidNotCrashes() {
        for (bad in listOf("", "R", "12", "R0", "R01", "R1..2", "R1.2.3.4", "R-1", "TOOLONGPREFIX1", "R99999999999", "R1.a")) {
            assertCode("invalid") { TaskId.parse(bad) }
        }
        assertNull(TaskId.parseOrNull("nope"))
        assertCode("invalid") { id("R1.2.3").child(1u) }
    }

    @Test
    fun statusTakesAliases() {
        assertEquals(Status.Todo, Status.parse("open"))
        assertEquals(Status.Todo, Status.parse("pending"))
        assertEquals(Status.Todo, Status.parse("todo"))
        assertEquals(Status.InProgress, Status.parse("in_progress"))
        assertEquals(Status.InProgress, Status.parse("In Progress"))
        assertEquals(Status.InProgress, Status.parse("doing"))
        assertEquals(Status.Done, Status.parse("completed"))
        for (w in listOf("wontdo", "won't-do", "not-planned", "dropped", "closed")) assertEquals(Status.Closed, Status.parse(w))
        assertCode("invalid") { Status.parse("later") }
        assertEquals("in-progress", Status.InProgress.toString())
    }

    @Test
    fun revIsSixteenHexCharsOfTheBytes() {
        val rev = Rev.of("hello".encodeToByteArray())
        assertEquals("2cf24dba5fb0a30e", rev.value)
        assertCode("invalid") { Rev("xyz") }
        assertCode("invalid") { Rev("ABCDEF0123456789") }
    }
}
