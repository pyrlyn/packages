package pyrlyn.fluent

import androidx.compose.ui.geometry.Rect
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class WindowZonesTest {
    private val bar = Rect(0f, 0f, 1000f, 48f)

    @Test
    fun anUnmarkedPointIsContent() = assertEquals(WindowZone.Content, WindowZones().at(10f, 10f))

    @Test
    fun aControlInTheBarKeepsItsClicks() {
        val zones = WindowZones()
        zones.mark("bar", WindowZone.Caption, bar)
        zones.mark("search", WindowZone.Content, Rect(300f, 8f, 700f, 40f))
        assertEquals(WindowZone.Caption, zones.at(100f, 10f))
        assertEquals(WindowZone.Content, zones.at(500f, 10f))
    }

    @Test
    fun aCaptionButtonWinsOverTheBar() {
        val zones = WindowZones()
        zones.mark("bar", WindowZone.Caption, bar)
        zones.mark("close", WindowZone.Close, Rect(954f, 0f, 1000f, 48f))
        assertEquals(WindowZone.Close, zones.at(970f, 20f))
    }

    @Test
    fun aMarkMovesWithItsElementAndGoesWithIt() {
        val zones = WindowZones()
        zones.mark("bar", WindowZone.Caption, bar)
        zones.mark("bar", WindowZone.Caption, bar.translate(0f, 100f))
        assertEquals(WindowZone.Content, zones.at(10f, 10f))
        assertEquals(WindowZone.Caption, zones.at(10f, 110f))
        zones.forget("bar")
        assertEquals(WindowZone.Content, zones.at(10f, 110f))
    }

    @Test
    fun aPressAndAReleaseOnAButtonClickIt() {
        var clicks = 0
        val zones = WindowZones()
        zones.mark("close", WindowZone.Close, bar) { clicks++ }
        zones.hover(WindowZone.Close)
        zones.press(WindowZone.Close)
        zones.release(WindowZone.Close)
        assertEquals(1, clicks)
    }

    @Test
    fun aPressThatLeavesItsButtonClicksNothing() {
        var clicks = 0
        val zones = WindowZones()
        zones.mark("close", WindowZone.Close, bar) { clicks++ }
        zones.press(WindowZone.Close)
        zones.hover(null)
        zones.hover(WindowZone.Close)
        zones.release(WindowZone.Close)
        assertEquals(0, clicks)
    }

    @Test
    fun aReleaseWithoutAPressClicksNothing() {
        var clicks = 0
        val zones = WindowZones()
        zones.mark("close", WindowZone.Close, bar) { clicks++ }
        zones.release(WindowZone.Close)
        assertEquals(0, clicks)
    }

    @Test
    fun aClickLeavesNoButtonHovered() {
        val zones = WindowZones()
        zones.press(WindowZone.Close)
        assertEquals(WindowZone.Close, zones.hovered)
        zones.release(WindowZone.Close)
        assertNull(zones.hovered)
    }
}
