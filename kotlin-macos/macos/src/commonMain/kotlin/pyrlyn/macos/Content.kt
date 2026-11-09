package pyrlyn.macos

import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.takeOrElse
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/** The colour a slot's text and icons take: white on an accent button, the error ink on a destructive one. */
val LocalMacContentColor = compositionLocalOf { Color.Unspecified }

/** The style a slot's text takes, such as Small emphasized in a regular button. */
val LocalMacTextStyle = compositionLocalOf { TextStyle.Default }

/** Sets [LocalMacContentColor] and [LocalMacTextStyle] for a component's slot. */
@Composable
fun ProvideMacContent(
    color: Color,
    style: TextStyle,
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(LocalMacContentColor provides color, LocalMacTextStyle provides style, content = content)
}

/** Text in the slot's colour and style unless it sets its own. */
@Composable
fun Label(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    style: TextStyle = LocalMacTextStyle.current,
    maxLines: Int = Int.MAX_VALUE,
) {
    BasicText(
        text,
        modifier,
        style.copy(color = color.takeOrElse { style.color.takeOrElse { LocalMacContentColor.current.takeOrElse { Mac.colors.textPrimary } } }),
        overflow = if (maxLines == Int.MAX_VALUE) TextOverflow.Clip else TextOverflow.Ellipsis,
        maxLines = maxLines,
    )
}

/** What a control is doing under the pointer and keyboard, read once per control. */
class ControlState(
    val hovered: Boolean,
    val pressed: Boolean,
    val focused: Boolean,
)

@Composable
fun InteractionSource.controlState(): ControlState {
    val hovered by collectIsHoveredAsState()
    val pressed by collectIsPressedAsState()
    val focused by collectIsFocusedAsState()
    return ControlState(hovered, pressed, focused)
}

/**
 * The file draws controls at rest only; hover and press lay a neutral veil over the fill, as AppKit does.
 * Null when the control is at rest.
 */
@Composable
fun ControlState.veil(): Color? {
    val dark = Mac.colors.dark
    return when {
        pressed -> if (dark) Color.White.copy(alpha = PRESS_DARK) else Color.Black.copy(alpha = PRESS_LIGHT)
        hovered -> if (dark) Color.White.copy(alpha = HOVER_DARK) else Color.Black.copy(alpha = HOVER_LIGHT)
        else -> null
    }
}

/** The keyboard focus ring: the accent around the control's own shape. */
@Composable
fun Modifier.focusRing(
    focused: Boolean,
    shape: Shape,
): Modifier = if (focused) border(FOCUS_WIDTH.dp, Mac.colors.accent.copy(alpha = FOCUS_ALPHA), shape) else this

private const val HOVER_DARK = 0.06f
private const val PRESS_DARK = 0.12f
private const val HOVER_LIGHT = 0.04f
private const val PRESS_LIGHT = 0.08f
private const val FOCUS_WIDTH = 2
private const val FOCUS_ALPHA = 0.8f

/** Disabled controls fade as in AppKit. */
internal const val DISABLED_ALPHA = 0.45f
