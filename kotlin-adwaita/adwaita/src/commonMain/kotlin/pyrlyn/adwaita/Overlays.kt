// The sizes are the design system's own values; a name for each would only repeat it.
@file:Suppress("MagicNumber")

package pyrlyn.adwaita

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import pyrlyn.glass.Glyph
import pyrlyn.glass.GlyphIcon

private val MenuShape = RoundedCornerShape(14.dp)

/** Hangs a popover under its anchor with their right edges together, the arrow over the button. */
private val UnderTheAnchor =
    object : PopupPositionProvider {
        override fun calculatePosition(
            anchorBounds: IntRect,
            windowSize: IntSize,
            layoutDirection: LayoutDirection,
            popupContentSize: IntSize,
        ): IntOffset {
            val x = (anchorBounds.right - popupContentSize.width + ANCHOR_SHIFT).coerceAtLeast(0)
            return IntOffset(x, anchorBounds.bottom)
        }
    }

private const val ANCHOR_SHIFT = 2

/** The popover a header's menu button opens. Place it beside the button: it hangs from the same parent. */
@Composable
fun PopoverMenu(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val c = LocalAdwaita.current
    Popup(UnderTheAnchor, onDismiss, PopupProperties(focusable = true)) {
        Box(Modifier.padding(start = 20.dp, end = 20.dp, bottom = 60.dp, top = 6.dp)) {
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .padding(end = 12.dp)
                    .size(12.dp)
                    .rotate(45f)
                    .background(c.overlay),
            )
            Column(
                modifier
                    .padding(top = 6.dp)
                    .width(280.dp)
                    .floating(MenuShape, Lift.Menu, c.overlay)
                    .border(1.dp, c.overlayLine, MenuShape)
                    .padding(6.dp),
                content = content,
            )
        }
    }
}

/** A line of a [PopoverMenu]: [glyph] or [leading], its text, and a [shortcut] at the end. */
@Composable
fun MenuItem(
    text: String,
    modifier: Modifier = Modifier,
    glyph: Glyph? = null,
    shortcut: String? = null,
    leading: (@Composable () -> Unit)? = null,
    onClick: () -> Unit,
) {
    val c = LocalAdwaita.current
    val type = LocalAdwaitaType.current
    val source = remember { MutableInteractionSource() }
    val hovered by source.collectIsHoveredAsState()
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = 38.dp)
            .clip(Corners.Small)
            .background(if (hovered) c.hover else Color.Transparent)
            .focusRing(source, Corners.Small)
            .hoverable(source)
            .clickable(source, null, role = Role.Button, onClick = onClick)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        leading?.invoke() ?: glyph?.let { GlyphIcon(it, c.ink2, 16.dp) }
        Text(text, color = c.ink, style = type.body, modifier = Modifier.weight(1f), maxLines = 1)
        shortcut?.let { Text(it, color = c.ink2, style = type.small) }
    }
}

@Composable
fun MenuSeparator() {
    Box(
        Modifier
            .padding(horizontal = 4.dp, vertical = 6.dp)
            .fillMaxWidth()
            .height(1.dp)
            .background(LocalAdwaita.current.fill2),
    )
}

@Composable
fun MenuLabel(text: String) {
    Text(text, color = LocalAdwaita.current.ink2, style = LocalAdwaitaType.current.small, modifier = Modifier.padding(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 4.dp))
}

/** What a running operation says, on a pill at the foot of the window. [content] is its spinner and words; [action] ends it. */
@Composable
fun Toast(
    modifier: Modifier = Modifier,
    action: @Composable RowScope.() -> Unit = {},
    content: @Composable RowScope.() -> Unit,
) {
    val shape = RoundedCornerShape(26.dp)
    Row(
        modifier
            .floating(shape, Lift.Toast, LocalAdwaita.current.toast, highlight = 0.12f)
            .padding(start = 20.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        content()
        action()
    }
}

/** How a [Banner] reads. */
enum class BannerKind { Warning, Success }

/** One thing the page wants read first, with what to do about it. */
@Composable
fun Banner(
    text: String,
    kind: BannerKind,
    modifier: Modifier = Modifier,
    leading: @Composable RowScope.() -> Unit = {},
    action: @Composable RowScope.() -> Unit = {},
) {
    val c = LocalAdwaita.current
    val tint = if (kind == BannerKind.Warning) c.warning else c.accent
    val alpha = if (kind == BannerKind.Warning) 0.16f else 0.14f
    Row(
        modifier
            .fillMaxWidth()
            .clip(Corners.Medium)
            .background(tint.copy(alpha = alpha))
            .border(1.dp, tint.copy(alpha = alpha * 2.2f), Corners.Medium)
            .padding(start = 18.dp, end = 12.dp, top = 12.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        leading()
        Text(text, color = c.ink, style = LocalAdwaitaType.current.bodyStrong, modifier = Modifier.weight(1f))
        action()
    }
}

/**
 * A centred question over a scrim. [footer] holds its buttons, each `Modifier.weight(1f)` for the design's two columns;
 * clicking the scrim is [onDismiss].
 */
@Composable
fun AlertDialog(
    title: String,
    onDismiss: () -> Unit,
    footer: @Composable RowScope.() -> Unit,
    modifier: Modifier = Modifier,
    leading: @Composable () -> Unit = {},
    content: @Composable ColumnScope.() -> Unit,
) {
    val c = LocalAdwaita.current
    Dialog(onDismiss, DialogProperties(usePlatformDefaultWidth = false, scrimColor = c.scrim)) {
        Box(
            Modifier.fillMaxSize().padding(24.dp).pointerInput(onDismiss) { detectTapGestures { onDismiss() } },
            contentAlignment = Alignment.Center,
        ) {
            Column(
                modifier
                    .widthIn(max = 420.dp)
                    .fillMaxWidth()
                    .floating(Corners.Large, Lift.Dialog, c.overlay)
                    .border(1.dp, c.overlayLine, Corners.Large)
                    // Keeps a tap on the dialog itself from reaching the scrim behind it.
                    .pointerInput(Unit) { detectTapGestures { } }
                    .padding(start = 24.dp, end = 24.dp, top = 28.dp, bottom = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                leading()
                Text(title, color = c.ink, style = LocalAdwaitaType.current.title3, textAlign = TextAlign.Center, modifier = Modifier.semantics { heading() })
                content()
                Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), content = footer)
            }
        }
    }
}

/** A [AlertDialog]'s text. */
@Composable
fun DialogText(
    text: String,
    modifier: Modifier = Modifier,
) {
    Text(text, color = LocalAdwaita.current.ink2, style = LocalAdwaitaType.current.body, textAlign = TextAlign.Center, modifier = modifier)
}
