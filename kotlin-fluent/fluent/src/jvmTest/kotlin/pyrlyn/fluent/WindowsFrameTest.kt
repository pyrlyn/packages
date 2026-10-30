package pyrlyn.fluent

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class WindowsFrameTest {
    private fun edge(
        x: Int,
        y: Int,
    ) = edgeAt(x, y, width = 1000, height = 600, side = 8, top = 4)

    @Test
    fun theMiddleOfTheWindowIsNotAnEdge() = assertEquals(HTCLIENT, edge(500, 300))

    @Test
    fun eachSideResizes() {
        assertEquals(HTLEFT, edge(0, 300))
        assertEquals(HTRIGHT, edge(999, 300))
        assertEquals(HTTOP, edge(500, 0))
        assertEquals(HTBOTTOM, edge(500, 599))
    }

    @Test
    fun eachCornerResizes() {
        assertEquals(HTTOPLEFT, edge(0, 0))
        assertEquals(HTTOPRIGHT, edge(999, 0))
        assertEquals(HTBOTTOMLEFT, edge(0, 599))
        assertEquals(HTBOTTOMRIGHT, edge(999, 599))
    }

    @Test
    fun theTopBandIsThinnerSoTheCaptionButtonsReachTheEdge() {
        assertEquals(HTTOP, edge(500, 3))
        assertEquals(HTCLIENT, edge(500, 4))
        assertEquals(HTCLIENT, edge(500, 7))
    }

    @Test
    fun zonesAreWhatTheSystemCallsThem() {
        assertEquals(HTCLOSE, hitCode(WindowZone.Close))
        assertEquals(HTMAXBUTTON, hitCode(WindowZone.Maximize))
        assertEquals(HTMINBUTTON, hitCode(WindowZone.Minimize))
        assertEquals(HTCAPTION, hitCode(WindowZone.Caption))
        assertEquals(HTCLIENT, hitCode(WindowZone.Content))
    }

    @Test
    fun onlyTheButtonsOfTheFrameAreCaptionButtons() {
        assertEquals(WindowZone.Close, captionButton(HTCLOSE.toLong()))
        assertEquals(WindowZone.Maximize, captionButton(HTMAXBUTTON.toLong()))
        assertEquals(WindowZone.Minimize, captionButton(HTMINBUTTON.toLong()))
        assertNull(captionButton(HTCAPTION.toLong()))
        assertNull(captionButton(HTCLIENT.toLong()))
    }

    @Test
    fun eachMaterialHasItsBackdrop() {
        assertEquals(DWMSBT_MAINWINDOW, backdropOf(WindowMaterial.Mica))
        assertEquals(DWMSBT_TRANSIENTWINDOW, backdropOf(WindowMaterial.Acrylic))
        assertEquals(DWMSBT_NONE, backdropOf(WindowMaterial.Transparent))
    }
}
