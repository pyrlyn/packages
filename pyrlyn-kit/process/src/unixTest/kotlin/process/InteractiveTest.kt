package process

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class InteractiveTest {
    @Test
    fun statusIsHowTheChildEnded() {
        assertEquals(ExitStatus.Exited(0), Command("sh", listOf("-c", "exit 0")).status())
        assertEquals(ExitStatus.Exited(4), Command("sh", listOf("-c", "exit \"\$KIT_CODE\"")).env("KIT_CODE", "4").status())
    }

    @Test
    fun aProgramThatIsNotThereCannotBeRun() {
        val e = assertFailsWith<ProcessException> { Command("kit-no-such-program").status() }
        assertTrue(e.detail.startsWith("could not run kit-no-such-program: "), e.detail)
    }

    @Test
    fun theTestBinaryKnowsWhereItIs() {
        val exe = assertNotNull(currentExe())
        assertTrue(exe.startsWith("/"), exe)
        assertTrue(exe.endsWith(".kexe"), exe)
    }
}
