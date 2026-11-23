@file:Suppress("ktlint:standard:function-naming", "FunctionNaming", "FunctionParameterNaming", "LongParameterList", "TooManyFunctions", "MatchingDeclarationName")

package pyrlyn.fluent

import com.sun.jna.Callback
import com.sun.jna.Library
import com.sun.jna.Native
import com.sun.jna.Pointer

// Only what the frame needs, spelled as Win32 spells it. jna-platform would bring thousands of bindings for these few.

internal interface WindowProcedure : Callback {
    fun callback(
        hwnd: Pointer,
        message: Int,
        wParam: Long,
        lParam: Long,
    ): Long
}

internal interface User32 : Library {
    fun GetWindowLongPtrW(
        hwnd: Pointer,
        index: Int,
    ): Long

    fun SetWindowLongPtrW(
        hwnd: Pointer,
        index: Int,
        value: Long,
    ): Long

    fun SetWindowLongPtrW(
        hwnd: Pointer,
        index: Int,
        value: WindowProcedure,
    ): Long

    fun CallWindowProcW(
        previous: Long,
        hwnd: Pointer,
        message: Int,
        wParam: Long,
        lParam: Long,
    ): Long

    fun PostMessageW(
        hwnd: Pointer,
        message: Int,
        wParam: Long,
        lParam: Long,
    ): Boolean

    fun SetWindowPos(
        hwnd: Pointer,
        insertAfter: Pointer?,
        x: Int,
        y: Int,
        width: Int,
        height: Int,
        flags: Int,
    ): Boolean

    fun TrackMouseEvent(event: LongArray): Boolean

    fun ScreenToClient(
        hwnd: Pointer,
        point: IntArray,
    ): Boolean

    fun GetClientRect(
        hwnd: Pointer,
        rect: IntArray,
    ): Boolean

    fun IsZoomed(hwnd: Pointer): Boolean

    fun GetDpiForWindow(hwnd: Pointer): Int

    fun GetSystemMetricsForDpi(
        index: Int,
        dpi: Int,
    ): Int

    fun GetSystemMenu(
        hwnd: Pointer,
        revert: Boolean,
    ): Pointer?

    fun EnableMenuItem(
        menu: Pointer,
        item: Int,
        enable: Int,
    ): Int

    fun SetMenuDefaultItem(
        menu: Pointer,
        item: Int,
        byPosition: Int,
    ): Boolean

    fun TrackPopupMenu(
        menu: Pointer,
        flags: Int,
        x: Int,
        y: Int,
        reserved: Int,
        hwnd: Pointer,
        rect: Pointer?,
    ): Int
}

internal interface Dwmapi : Library {
    fun DwmExtendFrameIntoClientArea(
        hwnd: Pointer,
        margins: IntArray,
    ): Int

    fun DwmSetWindowAttribute(
        hwnd: Pointer,
        attribute: Int,
        value: IntArray,
        size: Int,
    ): Int
}

internal interface Ntdll : Library {
    fun RtlGetVersion(info: IntArray): Int
}

internal fun user32() = Native.load("user32", User32::class.java)

internal fun dwmapi() = Native.load("dwmapi", Dwmapi::class.java)

internal fun ntdll() = Native.load("ntdll", Ntdll::class.java)

internal const val GWL_STYLE = -16
internal const val GWLP_WNDPROC = -4

internal const val WS_POPUP = 0x80000000L
internal const val WS_SYSMENU = 0x00080000L
internal const val WS_CAPTION = 0x00C00000L
internal const val WS_THICKFRAME = 0x00040000L
internal const val WS_MINIMIZEBOX = 0x00020000L
internal const val WS_MAXIMIZEBOX = 0x00010000L

internal const val SWP_REFRAME = 0x37 // SWP_NOSIZE, NOMOVE, NOZORDER, NOACTIVATE, FRAMECHANGED

internal const val WM_NCCALCSIZE = 0x0083
internal const val WM_NCHITTEST = 0x0084
internal const val WM_NCMOUSEMOVE = 0x00A0
internal const val WM_NCLBUTTONDOWN = 0x00A1
internal const val WM_NCLBUTTONUP = 0x00A2
internal const val WM_NCLBUTTONDBLCLK = 0x00A3
internal const val WM_NCRBUTTONDOWN = 0x00A4
internal const val WM_NCRBUTTONUP = 0x00A5
internal const val WM_SYSCOMMAND = 0x0112
internal const val WM_NCMOUSELEAVE = 0x02A2

// The first word of TRACKMOUSEEVENT: its size, then TME_LEAVE and TME_NONCLIENT.
internal const val TRACK_NONCLIENT_LEAVE = 0x12_0000_0018L

internal const val HTTRANSPARENT = -1
internal const val HTCLIENT = 1
internal const val HTCAPTION = 2
internal const val HTMINBUTTON = 8
internal const val HTMAXBUTTON = 9
internal const val HTLEFT = 10
internal const val HTRIGHT = 11
internal const val HTTOP = 12
internal const val HTTOPLEFT = 13
internal const val HTTOPRIGHT = 14
internal const val HTBOTTOM = 15
internal const val HTBOTTOMLEFT = 16
internal const val HTBOTTOMRIGHT = 17
internal const val HTCLOSE = 20

internal const val SC_SIZE = 0xF000
internal const val SC_MOVE = 0xF010
internal const val SC_MAXIMIZE = 0xF030
internal const val SC_CLOSE = 0xF060
internal const val SC_KEYMENU = 0xF100
internal const val SC_RESTORE = 0xF120
internal const val SC_MASK = 0xFFF0L
internal const val SPACE = 0x20L

internal const val MF_ENABLED = 0
internal const val MF_GRAYED = 1
internal const val TPM_RETURNCMD = 0x100

internal const val SM_CXSIZEFRAME = 32
internal const val SM_CYSIZEFRAME = 33
internal const val SM_CXPADDEDBORDER = 92

internal const val DWMWA_USE_IMMERSIVE_DARK_MODE = 20
internal const val DWMWA_WINDOW_CORNER_PREFERENCE = 33
internal const val DWMWA_CAPTION_COLOR = 35
internal const val DWMWA_SYSTEMBACKDROP_TYPE = 38
internal const val DWMWCP_ROUND = 2
internal const val DWMWA_COLOR_NONE = 0xFFFFFFFE.toInt()
internal const val DWMSBT_NONE = 1
internal const val DWMSBT_MAINWINDOW = 2
internal const val DWMSBT_TRANSIENTWINDOW = 3

internal const val VERSION_INFO_WORDS = 37
internal const val VERSION_INFO_SIZE = 148
internal const val VERSION_BUILD_WORD = 3
internal const val FIRST_WINDOWS_11_BUILD = 22000

internal const val RECT_RIGHT = 2
internal const val RECT_BOTTOM = 3
internal const val RECT_WORDS = 4
internal const val INT_BYTES = 4L
internal const val WORD_BITS = 16
