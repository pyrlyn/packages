package pyrlyn.fluent

import androidx.compose.ui.awt.ComposeWindow
import com.sun.jna.Native
import com.sun.jna.Pointer
import org.jetbrains.skiko.DelicateSkikoApi
import org.jetbrains.skiko.GraphicsApi
import org.jetbrains.skiko.SkiaLayer
import org.jetbrains.skiko.transparentWindowBackgroundHack
import java.awt.Component
import java.awt.Container
import javax.swing.SwingUtilities

/**
 * The frame Windows 11 gives its own windows, for an undecorated [ComposeWindow] that draws its title bar: rounded
 * corners, the shadow, Snap Layouts, the system menu, maximizing on any monitor and the material of the desktop.
 *
 * The window stays undecorated for AWT, so without this class it is still a plain, working window of its own.
 */
class WindowsFrame private constructor(
    private val window: Pointer,
    canvas: Pointer,
    private val zones: WindowZones,
    private val seeThrough: Boolean,
    private val user32: User32,
    private val dwm: Dwmapi,
) {
    // JNA holds a callback weakly: these fields keep the window procedures alive as long as the windows.
    private val frameProcedure = Subclass(window, ::onFrameMessage)

    // A child window takes the pointer first. The frame has to answer for the title bar: Snap Layouts only opens for a top-level window.
    private val canvasProcedure =
        Subclass(canvas) { message, _, lParam ->
            if (message == WM_NCHITTEST && hit(lParam) != HTCLIENT) HTTRANSPARENT.toLong() else null
        }

    private val systemMenu: Pointer?

    init {
        val style = user32.GetWindowLongPtrW(window, GWL_STYLE)
        // The system only hands out the menu of a window with WS_SYSMENU, and that has to go below.
        user32.SetWindowLongPtrW(window, GWL_STYLE, style or WS_SYSMENU)
        systemMenu = user32.GetSystemMenu(window, false)
        // Without WS_POPUP the system frames the window. Without WS_SYSMENU it draws no caption buttons of its own under ours.
        val framed = style and (WS_POPUP or WS_SYSMENU).inv() or WS_CAPTION or WS_THICKFRAME or WS_MINIMIZEBOX or WS_MAXIMIZEBOX
        user32.SetWindowLongPtrW(window, GWL_STYLE, framed)
        attribute(DWMWA_WINDOW_CORNER_PREFERENCE, DWMWCP_ROUND)
        attribute(DWMWA_CAPTION_COLOR, DWMWA_COLOR_NONE)
        user32.SetWindowPos(window, null, 0, 0, 0, 0, SWP_REFRAME)
    }

    /** Puts [material] behind the window. False leaves the window to its own wall: nothing shows through it. */
    fun wear(
        material: WindowMaterial,
        dark: Boolean,
    ): Boolean {
        attribute(DWMWA_USE_IMMERSIVE_DARK_MODE, if (dark) 1 else 0)
        val worn = seeThrough && attribute(DWMWA_SYSTEMBACKDROP_TYPE, backdropOf(material)) && extendFrame(WHOLE_WINDOW)
        if (!worn) {
            attribute(DWMWA_SYSTEMBACKDROP_TYPE, DWMSBT_NONE)
            extendFrame(EDGE_ONLY)
        }
        return worn
    }

    private fun attribute(
        name: Int,
        value: Int,
    ) = dwm.DwmSetWindowAttribute(window, name, intArrayOf(value), Int.SIZE_BYTES) == 0

    private fun extendFrame(margins: IntArray) = dwm.DwmExtendFrameIntoClientArea(window, margins) == 0

    private fun onFrameMessage(
        message: Int,
        wParam: Long,
        lParam: Long,
    ): Long? =
        when (message) {
            WM_NCCALCSIZE -> {
                if (wParam == 0L) {
                    null
                } else {
                    keepOnTheScreen(Pointer(lParam))
                    0L
                }
            }

            WM_NCHITTEST -> {
                hit(lParam).toLong()
            }

            WM_NCMOUSEMOVE, WM_NCMOUSELEAVE, WM_NCLBUTTONDOWN, WM_NCLBUTTONDBLCLK, WM_NCLBUTTONUP -> {
                onCaptionPointer(message, captionButton(wParam))
            }

            // The system's handling of the press would swallow the release.
            WM_NCRBUTTONDOWN -> {
                if (wParam == HTCAPTION.toLong()) 0L else null
            }

            WM_NCRBUTTONUP -> {
                if (wParam == HTCAPTION.toLong()) {
                    val (x, y) = pointOf(lParam)
                    showSystemMenu(x, y)
                    0L
                } else {
                    null
                }
            }

            WM_SYSCOMMAND -> {
                if (wParam and SC_MASK == SC_KEYMENU.toLong() && lParam == SPACE) openKeyboardMenu(wParam, lParam) else null
            }

            else -> {
                null
            }
        }

    private fun onCaptionPointer(
        message: Int,
        button: WindowZone?,
    ): Long? =
        when {
            message == WM_NCMOUSEMOVE || message == WM_NCMOUSELEAVE -> {
                // Nothing else says when the pointer leaves for the content or the desktop.
                if (button != null) user32.TrackMouseEvent(longArrayOf(TRACK_NONCLIENT_LEAVE, Pointer.nativeValue(window), 0))
                onZones { hover(button) }
                // Null: the system opens Snap Layouts from the frame's own handling of a move.
                null
            }

            button == null -> {
                null
            }

            else -> {
                onZones { if (message == WM_NCLBUTTONUP) release(button) else press(button) }
                // Answered: the system would press a button of its own, which the title bar does not have.
                0L
            }
        }

    /** Left to the system: a menu of our own closes as Alt comes up, and the system's only runs for a window with WS_SYSMENU. */
    private fun openKeyboardMenu(
        wParam: Long,
        lParam: Long,
    ): Long {
        user32.SetWindowLongPtrW(window, GWL_STYLE, user32.GetWindowLongPtrW(window, GWL_STYLE) or WS_SYSMENU)
        val answer = frameProcedure.forward(WM_SYSCOMMAND, wParam, lParam)
        // Read again: the menu may have just maximized the window.
        user32.SetWindowLongPtrW(window, GWL_STYLE, user32.GetWindowLongPtrW(window, GWL_STYLE) and WS_SYSMENU.inv())
        return answer
    }

    /** Window procedures run on AWT's native thread, and these changes reach composition and the app's own actions. */
    private fun onZones(change: WindowZones.() -> Unit) = SwingUtilities.invokeLater { zones.change() }

    private fun hit(lParam: Long): Int {
        val point = pointOf(lParam)
        user32.ScreenToClient(window, point)
        val (x, y) = point
        if (!user32.IsZoomed(window)) {
            val client = IntArray(RECT_WORDS)
            user32.GetClientRect(window, client)
            val dpi = user32.GetDpiForWindow(window)
            val top = user32.GetSystemMetricsForDpi(SM_CYSIZEFRAME, dpi)
            val edge = edgeAt(x, y, client[RECT_RIGHT], client[RECT_BOTTOM], sideBand(dpi), top)
            if (edge != HTCLIENT) return edge
        }
        return hitCode(zones.at(x.toFloat(), y.toFloat()))
    }

    private fun sideBand(dpi: Int) = user32.GetSystemMetricsForDpi(SM_CXSIZEFRAME, dpi) + user32.GetSystemMetricsForDpi(SM_CXPADDEDBORDER, dpi)

    /** The client area is the whole window, but a maximized window hangs its frame off the screen on every side. */
    private fun keepOnTheScreen(rect: Pointer) {
        if (!user32.IsZoomed(window)) return
        val dpi = user32.GetDpiForWindow(window)
        val x = sideBand(dpi)
        val y = user32.GetSystemMetricsForDpi(SM_CYSIZEFRAME, dpi) + user32.GetSystemMetricsForDpi(SM_CXPADDEDBORDER, dpi)
        intArrayOf(x, y, -x, -y).forEachIndexed { side, inset ->
            val offset = side * INT_BYTES
            rect.setInt(offset, rect.getInt(offset) + inset)
        }
    }

    private fun showSystemMenu(
        x: Int,
        y: Int,
    ) {
        val menu = systemMenu ?: return
        val maximized = user32.IsZoomed(window)
        val whileMaximized = if (maximized) MF_ENABLED else MF_GRAYED
        val whileRestored = if (maximized) MF_GRAYED else MF_ENABLED
        user32.EnableMenuItem(menu, SC_RESTORE, whileMaximized)
        for (item in listOf(SC_MOVE, SC_SIZE, SC_MAXIMIZE)) user32.EnableMenuItem(menu, item, whileRestored)
        user32.SetMenuDefaultItem(menu, SC_CLOSE, 0)
        val command = user32.TrackPopupMenu(menu, TPM_RETURNCMD, x, y, 0, window, null)
        if (command != 0) user32.PostMessageW(window, WM_SYSCOMMAND, command.toLong(), 0)
    }

    private fun pointOf(lParam: Long) = intArrayOf(lParam.toShort().toInt(), (lParam shr WORD_BITS).toShort().toInt())

    /** Takes [hwnd]'s messages; a message [handle] answers null for, or fails on, goes on to the window's own procedure. */
    private inner class Subclass(
        private val hwnd: Pointer,
        private val handle: (Int, Long, Long) -> Long?,
    ) : WindowProcedure {
        private val previous = user32.GetWindowLongPtrW(hwnd, GWLP_WNDPROC)

        init {
            user32.SetWindowLongPtrW(hwnd, GWLP_WNDPROC, this)
        }

        override fun callback(
            hwnd: Pointer,
            message: Int,
            wParam: Long,
            lParam: Long,
        ) = runCatching { handle(message, wParam, lParam) }.getOrNull() ?: forward(message, wParam, lParam)

        fun forward(
            message: Int,
            wParam: Long,
            lParam: Long,
        ) = user32.CallWindowProcW(previous, hwnd, message, wParam, lParam)
    }

    companion object {
        /** Whether this system has the frame. Such a window has to be `transparent`, which AWT only allows before it exists. */
        val available: Boolean by lazy {
            System.getProperty("os.name").orEmpty().startsWith("Windows") && runCatching { ntdll().isWindows11() }.getOrDefault(false)
        }

        /**
         * Frames [window], whose native windows exist but which is not on the screen yet. Null where the system has no frame
         * or anything fails, which leaves the window as it was: a frame is not worth losing the app.
         */
        @OptIn(DelicateSkikoApi::class)
        fun attach(
            window: ComposeWindow,
            zones: WindowZones,
        ): WindowsFrame? {
            if (!available) return null
            return runCatching {
                val layer = checkNotNull(window.skiaLayer()) { "The window has no Skia layer" }
                // Compose set this for the API it expected. Where that one failed, a layered window hides the canvas of the next.
                window.background = transparentWindowBackgroundHack(layer.renderApi)
                WindowsFrame(
                    window = Native.getComponentPointer(window),
                    canvas = Native.getComponentPointer(layer.canvas),
                    zones = zones,
                    seeThrough = layer.transparency && layer.renderApi in SEE_THROUGH,
                    user32 = user32(),
                    dwm = dwmapi(),
                )
            }.onFailure { System.err.println("The window keeps its own frame: $it") }.getOrNull()
        }

        private fun Ntdll.isWindows11(): Boolean {
            val info = IntArray(VERSION_INFO_WORDS).also { it[0] = VERSION_INFO_SIZE }
            return RtlGetVersion(info) == 0 && info[VERSION_BUILD_WORD] >= FIRST_WINDOWS_11_BUILD
        }

        private fun Component.skiaLayer(): SkiaLayer? = this as? SkiaLayer ?: (this as? Container)?.components?.firstNotNullOfOrNull { it.skiaLayer() }

        private val SEE_THROUGH = setOf(GraphicsApi.DIRECT3D, GraphicsApi.ANGLE, GraphicsApi.OPENGL)

        // MARGINS: left, right, top, bottom. -1 turns the whole window into frame, one pixel keeps the shadow.
        private val WHOLE_WINDOW = intArrayOf(-1, -1, -1, -1)
        private val EDGE_ONLY = intArrayOf(0, 0, 1, 0)
    }
}

internal fun backdropOf(material: WindowMaterial) =
    when (material) {
        WindowMaterial.Mica -> DWMSBT_MAINWINDOW
        WindowMaterial.Acrylic -> DWMSBT_TRANSIENTWINDOW
        WindowMaterial.Transparent -> DWMSBT_NONE
    }

internal fun hitCode(zone: WindowZone) =
    when (zone) {
        WindowZone.Close -> HTCLOSE
        WindowZone.Maximize -> HTMAXBUTTON
        WindowZone.Minimize -> HTMINBUTTON
        WindowZone.Content -> HTCLIENT
        WindowZone.Caption -> HTCAPTION
    }

/** The caption button a hit code of the frame stands for, if any. */
internal fun captionButton(hit: Long) =
    when (hit.toInt()) {
        HTCLOSE -> WindowZone.Close
        HTMAXBUTTON -> WindowZone.Maximize
        HTMINBUTTON -> WindowZone.Minimize
        else -> null
    }

/** The resize edge at a point of a window [width] by [height], or [HTCLIENT] inside the bands. */
internal fun edgeAt(
    x: Int,
    y: Int,
    width: Int,
    height: Int,
    side: Int,
    top: Int,
): Int {
    val atLeft = x < side
    val atRight = x >= width - side
    return when {
        y < top && atLeft -> HTTOPLEFT
        y < top && atRight -> HTTOPRIGHT
        y < top -> HTTOP
        y >= height - side && atLeft -> HTBOTTOMLEFT
        y >= height - side && atRight -> HTBOTTOMRIGHT
        y >= height - side -> HTBOTTOM
        atLeft -> HTLEFT
        atRight -> HTRIGHT
        else -> HTCLIENT
    }
}
