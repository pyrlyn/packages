package pyrlyn.fluent

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import kotlin.concurrent.Volatile

/** What a point of a self-drawn title bar is to the system, the zone that wins an overlap first. */
enum class WindowZone { Close, Maximize, Minimize, Content, Caption }

/**
 * Where a window that draws its own title bar keeps it, in window pixels: the system has to know which pixels move the
 * window and which are its caption buttons. Composition writes; the system's hit test reads, on a thread of its own.
 */
class WindowZones {
    private class Marked(
        val zone: WindowZone,
        val bounds: Rect,
        val onClick: () -> Unit,
    )

    @Volatile
    private var marked = emptyMap<Any, Marked>()

    /** The caption button under the pointer. The system's frame keeps those from the content, so the frame says it here. */
    var hovered by mutableStateOf<WindowZone?>(null)
        private set

    private var pressed: WindowZone? = null

    /** A point nothing marks is content: a window would rather miss a drag than eat a click. */
    fun at(
        x: Float,
        y: Float,
    ) = marked.values.filter { it.bounds.contains(Offset(x, y)) }.minOfOrNull { it.zone } ?: WindowZone.Content

    fun mark(
        key: Any,
        zone: WindowZone,
        bounds: Rect,
        onClick: () -> Unit = {},
    ) {
        marked = marked + (key to Marked(zone, bounds, onClick))
    }

    fun forget(key: Any) {
        marked = marked - key
    }

    fun hover(zone: WindowZone?) {
        if (zone != hovered) pressed = null
        hovered = zone
    }

    fun press(zone: WindowZone) {
        hover(zone)
        pressed = zone
    }

    /** Clicks [zone] if the press stayed on it. The pointer counts as gone: a click may take the window from under it. */
    fun release(zone: WindowZone) {
        val clicked = pressed == zone
        hover(null)
        if (clicked) {
            marked.values
                .firstOrNull { it.zone == zone }
                ?.onClick
                ?.invoke()
        }
    }
}

/** The zones of the window this content is in; none where the system's frame is not in play. */
val LocalWindowZones = staticCompositionLocalOf<WindowZones?> { null }

/** Makes this element [zone] for the system. [onClick] is what a click on a caption button does when the system's frame takes it. */
@Composable
fun Modifier.windowZone(
    zone: WindowZone,
    onClick: () -> Unit = {},
): Modifier {
    val zones = LocalWindowZones.current ?: return this
    val key = remember { Any() }
    val click by rememberUpdatedState(onClick)
    DisposableEffect(zones, key) { onDispose { zones.forget(key) } }
    return onGloballyPositioned { zones.mark(key, zone, it.boundsInWindow()) { click() } }
}
