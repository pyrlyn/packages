package icons

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class IconsTest {
    @Test
    fun eachVerbGetsItsIcon() {
        val cases =
            listOf(
                "uninstalling" to "🧹",
                "removed" to "🧹",
                "pruning" to "🧹",
                "installed" to "📦",
                "upgrading" to "⏫",
                "updated" to "⏫",
                "downloading" to "⏬",
                "fetching" to "⏬",
                "linked" to "🔗",
                "rolled back" to "⏪",
                "searching" to "🔍",
                "doctor" to "🩺",
            )
        for ((verb, want) in cases) assertEquals(want, icon(verb), verb)
    }

    @Test
    fun uninstallWinsOverInstallAndCaseIsIgnored() {
        assertEquals("🧹", icon("Uninstalled"))
        assertEquals("📦", icon("ALREADY INSTALLED"))
    }

    @Test
    fun unknownVerbHasNoIcon() {
        assertNull(icon("thinking"))
        assertNull(icon(""))
    }

    @Test
    fun callerRowsExtendTheLookup() {
        val rows = listOf("index" to "📚")
        assertEquals("📚", iconIn(rows, "indexing"))
        assertNull(iconIn(rows, "installing"))
    }

    @Test
    fun everyIconFillsTheSameWidth() {
        for ((_, icon) in OPERATION_ICONS) {
            assertEquals(ICON_WIDTH, displayWidth(icon), icon)
            assertEquals(0, padding(icon), icon)
        }
    }

    @Test
    fun narrowIconIsPaddedAndEmptyIsBlank() {
        assertEquals(1, padding("→"))
        assertEquals("→  ", gutter("→"))
        assertEquals("📦 ", gutter("📦"))
        assertEquals("   ", gutter(null))
    }

    @Test
    fun guttersWithAndWithoutIconHaveEqualWidth() {
        val with = displayWidth(gutter(icon("install")))
        val without = displayWidth(gutter(null))
        assertEquals(with, without)
        assertEquals(ICON_WIDTH + 1, with)
    }

    @Test
    fun widthCountsWideNarrowAndZeroWidthCodePoints() {
        assertEquals(5, displayWidth("ab日c"))
        assertEquals(2, displayWidth("⬆️"))
        assertEquals(1, displayWidth("é"))
        assertEquals(0, displayWidth(""))
    }
}
