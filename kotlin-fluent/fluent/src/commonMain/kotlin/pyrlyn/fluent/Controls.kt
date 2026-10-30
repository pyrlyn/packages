// The offsets and alphas are the design system's own values; a name for each would only repeat it.
@file:Suppress("MagicNumber")

// The controls. Each is a real control: reachable by keyboard, with a focus ring, named for screen readers.

package pyrlyn.fluent

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.draw.innerShadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import pyrlyn.glass.Glyph
import pyrlyn.glass.GlyphIcon
import pyrlyn.glass.keyboardFocus

/** The focus ring, shown for the keyboard only. */
@Composable
internal fun Modifier.focusRing(
    source: MutableInteractionSource,
    shape: Shape,
): Modifier = if (keyboardFocus(source)) border(2.dp, LocalFluent.current.ink, shape) else this

/** The control's fill and its darker bottom edge. */
@Composable
private fun Modifier.controlEdge(
    fill: Color,
    stroke: Color,
    bottom: Color,
    shape: Shape,
): Modifier =
    clip(shape)
        .background(fill)
        .border(1.dp, stroke, shape)
        .innerShadow(shape, Shadow(radius = 0.dp, color = bottom, offset = DpOffset(0.dp, (-1).dp)))

enum class ButtonStyle { Standard, Accent, Update, Subtle, Danger, DangerFill, Glass }

private class ButtonLook(
    val fill: Color,
    val hover: Color,
    val pressed: Color,
    val ink: Color,
    val stroke: Color,
    val bottom: Color,
    val strong: Boolean = false,
)

@Composable
private fun ButtonStyle.look(): ButtonLook {
    val c = LocalFluent.current
    val filled = Color.White.copy(alpha = 0.1f)
    val shaded = Color.Black.copy(alpha = 0.38f)
    return when (this) {
        ButtonStyle.Standard -> ButtonLook(c.control, c.controlHover, c.control, c.ink, c.stroke, c.controlBottom)
        ButtonStyle.Accent -> ButtonLook(c.accent, c.accentHover, c.accentPressed, c.accentOn, filled, shaded, strong = true)
        ButtonStyle.Update -> ButtonLook(c.update, c.update.copy(alpha = 0.9f), c.update.copy(alpha = 0.8f), c.updateOn, filled, shaded, strong = true)
        ButtonStyle.Subtle -> ButtonLook(Color.Transparent, c.hover, c.selected, c.ink, Color.Transparent, Color.Transparent)
        ButtonStyle.Danger -> ButtonLook(Color.Transparent, c.hover, c.selected, c.danger, Color.Transparent, Color.Transparent)
        ButtonStyle.DangerFill -> ButtonLook(FluentColors.DangerFill, FluentColors.DangerFillHover, FluentColors.DangerFillHover, Color.White, filled, shaded, strong = true)
        ButtonStyle.Glass -> ButtonLook(Color.White.copy(alpha = 0.12f), Color.White.copy(alpha = 0.2f), Color.White.copy(alpha = 0.16f), Color.White, Color.White.copy(alpha = 0.22f), Color.White.copy(alpha = 0.1f))
    }
}

@Composable
fun FluentButton(
    text: String,
    style: ButtonStyle = ButtonStyle.Standard,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    large: Boolean = false,
    onClick: () -> Unit,
) {
    val type = LocalFluentType.current
    val look = style.look()
    val source = remember { MutableInteractionSource() }
    val hovered by source.collectIsHoveredAsState()
    val pressed by source.collectIsPressedAsState()
    val fill =
        when {
            !enabled -> look.fill
            pressed -> look.pressed
            hovered -> look.hover
            else -> look.fill
        }
    Row(
        modifier
            .graphicsLayer { alpha = if (enabled) 1f else DISABLED }
            .heightIn(min = if (large) 44.dp else 36.dp)
            .controlEdge(fill, look.stroke, look.bottom, Corners.Control)
            .focusRing(source, Corners.Control)
            .hoverable(source)
            .clickable(source, null, enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = if (large) 22.dp else 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        val weight = if (look.strong) FontWeight.SemiBold else FontWeight.Normal
        Text(text, color = look.ink, style = (if (large) type.body.copy(fontSize = type.strong.fontSize) else type.body).copy(fontWeight = weight), maxLines = 1)
    }
}

/** A disabled button saying what is under way, with its ring. */
@Composable
fun BusyButton(
    text: String,
    modifier: Modifier = Modifier,
) {
    val c = LocalFluent.current
    Row(
        modifier.heightIn(min = 36.dp).controlEdge(c.control, c.stroke, c.controlBottom, Corners.Control).padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        ProgressRing()
        Text(text, color = c.accentInk, style = LocalFluentType.current.body, maxLines = 1)
    }
}

/** A glyph-only button, named by [label]. */
@Composable
fun FluentIconButton(
    glyph: Glyph,
    label: String,
    modifier: Modifier = Modifier,
    tint: Color = LocalFluent.current.ink2,
    hover: Color = LocalFluent.current.hover,
    width: Dp = 36.dp,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    val source = remember { MutableInteractionSource() }
    val hovered by source.collectIsHoveredAsState()
    Box(
        modifier
            .size(width, 36.dp)
            .clip(Corners.Control)
            .background(if (hovered && enabled) hover else Color.Transparent)
            .focusRing(source, Corners.Control)
            .hoverable(source)
            .clickable(source, null, enabled, role = Role.Button, onClick = onClick)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) { GlyphIcon(glyph, if (enabled) tint else tint.copy(alpha = DISABLED_INK), 16.dp) }
}

/** The word-with-a-chevron link at the end of a heading. */
@Composable
fun FluentLink(
    text: String,
    modifier: Modifier = Modifier,
    glyph: Glyph? = null,
    onClick: () -> Unit,
) {
    val c = LocalFluent.current
    val source = remember { MutableInteractionSource() }
    val hovered by source.collectIsHoveredAsState()
    Row(
        modifier
            .heightIn(min = 36.dp)
            .clip(Corners.Control)
            .background(if (hovered) c.hover else Color.Transparent)
            .focusRing(source, Corners.Control)
            .hoverable(source)
            .clickable(source, null, role = Role.Button, onClick = onClick)
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(text, color = c.accentInk, style = LocalFluentType.current.body)
        glyph?.let { GlyphIcon(it, c.accentInk, 14.dp) }
    }
}

/**
 * The search box, in the title bar or at a page's head. It keeps its own text state so a keystroke is never lost to the
 * caller's next frame; a [value] the caller changes itself replaces the text.
 */
@Composable
fun SearchBox(
    value: String,
    placeholder: String,
    modifier: Modifier = Modifier,
    focus: FocusRequester? = null,
    onChange: (String) -> Unit,
) {
    val c = LocalFluent.current
    val type = LocalFluentType.current
    val source = remember { MutableInteractionSource() }
    val focused by source.collectIsFocusedAsState()
    val state = rememberTextFieldState(value)
    LaunchedEffect(value) { if (value != state.text.toString()) state.setTextAndPlaceCursorAtEnd(value) }
    LaunchedEffect(state) { snapshotFlow { state.text.toString() }.collect { if (it != value) onChange(it) } }
    val fill = if (focused) c.cardHover else c.control
    BasicTextField(
        state,
        modifier = (focus?.let { modifier.focusRequester(it) } ?: modifier).height(34.dp).semantics { contentDescription = placeholder },
        interactionSource = source,
        textStyle = type.body.copy(color = c.ink),
        cursorBrush = SolidColor(c.accent),
        lineLimits = TextFieldLineLimits.SingleLine,
        decorator = { field ->
            Row(
                Modifier
                    .fillMaxHeight()
                    .controlEdge(fill, c.stroke, if (focused) c.accent else c.controlBottom, Corners.Control)
                    .then(if (focused) Modifier.innerShadow(Corners.Control, Shadow(radius = 0.dp, color = c.accent, offset = DpOffset(0.dp, (-2).dp))) else Modifier)
                    .padding(start = 12.dp, end = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                    if (state.text.isEmpty()) Text(placeholder, color = c.ink2, style = type.body, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    field()
                }
                GlyphIcon(Glyph.Search, c.ink2, 16.dp)
            }
        },
    )
}

/** On or off, with the state in words beside the track. Named by [label], which the row around it shows. */
@Composable
fun ToggleSwitch(
    checked: Boolean,
    label: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onLabel: String = "On",
    offLabel: String = "Off",
    onChange: (Boolean) -> Unit,
) {
    val c = LocalFluent.current
    val source = remember { MutableInteractionSource() }
    val hovered by source.collectIsHoveredAsState()
    val thumb = if (hovered || checked) 14.dp else 12.dp
    Row(
        modifier
            .heightIn(min = 36.dp)
            .graphicsLayer { alpha = if (enabled) 1f else DISABLED }
            .hoverable(source)
            .toggleable(checked, source, null, enabled, Role.Switch) { onChange(it) }
            .semantics { contentDescription = label },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(if (checked) onLabel else offLabel, color = c.ink, style = LocalFluentType.current.body)
        val shape = RoundedCornerShape(10.dp)
        Box(
            Modifier
                .size(40.dp, 20.dp)
                .clip(shape)
                .background(if (checked) c.accent else c.control)
                .border(1.dp, if (checked) c.accent else c.ink2, shape)
                .focusRing(source, shape),
            contentAlignment = Alignment.CenterStart,
        ) {
            Box(
                Modifier
                    .offset(x = if (checked) 22.dp else 3.dp)
                    .size(thumb)
                    .clip(CircleShape)
                    .background(if (checked) c.accentOn else c.ink2),
            )
        }
    }
}

/** Picks one of [options] from a menu. */
@Composable
fun <T> ComboBox(
    options: List<T>,
    selected: T,
    label: (T) -> String,
    modifier: Modifier = Modifier,
    optionModifier: (T) -> Modifier = { Modifier },
    onPick: (T) -> Unit,
) {
    val c = LocalFluent.current
    val type = LocalFluentType.current
    var open by remember { mutableStateOf(false) }
    val source = remember { MutableInteractionSource() }
    val hovered by source.collectIsHoveredAsState()
    Box {
        Row(
            modifier
                .widthIn(min = 200.dp)
                .heightIn(min = 36.dp)
                .controlEdge(if (hovered) c.controlHover else c.control, c.stroke, c.controlBottom, Corners.Control)
                .focusRing(source, Corners.Control)
                .hoverable(source)
                .clickable(source, null, role = Role.DropdownList) { open = true }
                .padding(start = 12.dp, end = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(label(selected), color = c.ink, style = type.body, modifier = Modifier.weight(1f, fill = false))
            GlyphIcon(Glyph.ChevronDown, c.ink2, 12.dp)
        }
        DropdownMenu(open, { open = false }, containerColor = c.solid) {
            for (option in options) {
                DropdownMenuItem(
                    { Text(label(option), color = c.ink, style = type.body) },
                    onClick = {
                        open = false
                        onPick(option)
                    },
                    modifier = optionModifier(option),
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FluentSlider(
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    label: String,
    modifier: Modifier = Modifier,
    onChange: (Float) -> Unit,
) {
    val c = LocalFluent.current
    Slider(
        value = value,
        onValueChange = onChange,
        modifier = modifier.semantics { contentDescription = label },
        valueRange = range,
        thumb = {
            Box(
                Modifier
                    .size(20.dp)
                    .dropShadow(CircleShape, Shadow(radius = 3.dp, color = Color.Black.copy(alpha = 0.3f), offset = DpOffset(0.dp, 1.dp)))
                    .clip(CircleShape)
                    .background(c.thumbRing),
                contentAlignment = Alignment.Center,
            ) { Box(Modifier.size(10.dp).clip(CircleShape).background(c.accent)) }
        },
        track = { state ->
            val span = state.valueRange.endInclusive - state.valueRange.start
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(c.track),
            ) {
                Box(Modifier.fillMaxWidth((state.value - state.valueRange.start) / span).fillMaxHeight().background(c.accent))
            }
        },
    )
}

@Composable
fun <T> RadioButtons(
    options: List<T>,
    selected: T,
    label: (T) -> String,
    modifier: Modifier = Modifier,
    optionModifier: (T) -> Modifier = { Modifier },
    onPick: (T) -> Unit,
) {
    val c = LocalFluent.current
    FlowRow(modifier.selectableGroup(), horizontalArrangement = Arrangement.spacedBy(22.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        for (option in options) {
            val on = option == selected
            val source = remember { MutableInteractionSource() }
            Row(
                optionModifier(option)
                    .heightIn(min = 36.dp)
                    .selectable(on, source, null, role = Role.RadioButton) { onPick(option) },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Box(
                    Modifier
                        .size(20.dp)
                        .clip(CircleShape)
                        .background(if (on) c.accentOn else c.control)
                        .border(if (on) 6.dp else 1.dp, if (on) c.accent else c.ink2, CircleShape)
                        .focusRing(source, CircleShape),
                )
                Text(label(option), color = c.ink, style = LocalFluentType.current.body)
            }
        }
    }
}

/** A colour to pick; a null [color] is the clear swatch. */
@Composable
fun Swatch(
    color: Color?,
    label: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val c = LocalFluent.current
    val source = remember { MutableInteractionSource() }
    val fill = color ?: Color.White.copy(alpha = 0.5f)
    Box(
        modifier
            .size(40.dp)
            .then(if (selected) Modifier.border(2.dp, c.ink, RoundedCornerShape(6.dp)) else Modifier)
            .padding(if (selected) 4.dp else 0.dp)
            .dropShadow(Corners.Control, Shadow(radius = 10.dp, color = SWATCH_SHADOW, offset = DpOffset(0.dp, 6.dp)))
            .clip(Corners.Control)
            .background(fill)
            .innerShadow(Corners.Control, Shadow(radius = 0.dp, color = Color.White.copy(alpha = 0.35f), offset = DpOffset(0.dp, 1.dp)))
            .border(1.dp, Color.Black.copy(alpha = 0.12f), Corners.Control)
            .focusRing(source, Corners.Control)
            .selectable(selected, source, null, role = Role.RadioButton, onClick = onClick)
            .semantics { contentDescription = label },
    )
}

/** Switches between the views of a page: a row of words with a small bar under the chosen one. */
@Composable
fun <T> SelectorBar(
    items: List<T>,
    selected: T,
    label: (T) -> String,
    modifier: Modifier = Modifier,
    itemModifier: (T) -> Modifier = { Modifier },
    onPick: (T) -> Unit,
) {
    val c = LocalFluent.current
    val type = LocalFluentType.current
    Row(modifier.selectableGroup(), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        for (item in items) {
            val on = item == selected
            val source = remember { MutableInteractionSource() }
            val hovered by source.collectIsHoveredAsState()
            Box(
                itemModifier(item)
                    .heightIn(min = 38.dp)
                    .clip(Corners.Control)
                    .background(if (hovered) c.hover else Color.Transparent)
                    .focusRing(source, Corners.Control)
                    .hoverable(source)
                    .selectable(on, source, null, role = Role.Tab) { onPick(item) }
                    .padding(horizontal = 14.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(label(item), color = if (on) c.ink else c.ink2, style = type.body.copy(fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal))
                if (on) {
                    Box(
                        Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 2.dp)
                            .size(16.dp, 3.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(c.accent),
                    )
                }
            }
        }
    }
}

enum class BadgeKind { Success, Update, Neutral, Warning, Danger, Info }

/** A status in words on a soft fill. */
@Composable
fun StatusBadge(
    text: String,
    kind: BadgeKind,
    modifier: Modifier = Modifier,
) {
    val c = LocalFluent.current
    val (fill, ink) =
        when (kind) {
            BadgeKind.Success -> c.successSubtle to c.success
            BadgeKind.Update -> c.updateSubtle to c.updateInk
            BadgeKind.Neutral -> c.selected to c.ink2
            BadgeKind.Warning -> c.warningSubtle to c.warning
            BadgeKind.Danger -> c.dangerSubtle to c.danger
            BadgeKind.Info -> c.infoSubtle to c.accentInk
        }
    Box(
        modifier
            .heightIn(min = 22.dp)
            .clip(Corners.Control)
            .background(fill)
            .padding(horizontal = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = ink, style = LocalFluentType.current.badge, maxLines = 1)
    }
}

/** A fact in a thin frame: a count, a path. */
@Composable
fun Pill(
    text: String,
    modifier: Modifier = Modifier,
    code: Boolean = false,
) {
    val c = LocalFluent.current
    val type = LocalFluentType.current
    Box(
        modifier
            .heightIn(min = 28.dp)
            .clip(Corners.Control)
            .background(c.control)
            .border(1.dp, c.stroke, Corners.Control)
            .padding(horizontal = 10.dp),
        contentAlignment = Alignment.Center,
    ) { Text(text, color = c.ink2, style = if (code) type.mono else type.caption, maxLines = 1) }
}

/** The state that stands where a button would: a package already installed, an update done. */
@Composable
fun DoneMark(
    text: String,
    modifier: Modifier = Modifier,
) {
    val c = LocalFluent.current
    Row(
        modifier
            .heightIn(min = 36.dp)
            .clip(Corners.Control)
            .background(c.successSubtle)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        GlyphIcon(Glyph.Check, c.success, 14.dp)
        Text(text, color = c.success, style = LocalFluentType.current.small.copy(fontWeight = FontWeight.SemiBold), maxLines = 1)
    }
}

/** A release's version on a coral tag, standing askew. */
@Composable
fun ReleaseTag(
    version: String,
    modifier: Modifier = Modifier,
    tilt: Float = -8f,
    style: TextStyle? = null,
) {
    val type = LocalFluentType.current
    val shape = Corners.Item
    Box(
        modifier
            .graphicsLayer { rotationZ = tilt }
            .heightIn(min = 30.dp)
            .dropShadow(shape, Shadow(radius = 22.dp, color = Color(14, 40, 56, 77), offset = DpOffset(0.dp, 14.dp)))
            .dropShadow(shape, Shadow(radius = 0.dp, color = Color.Black.copy(alpha = 0.1f), offset = DpOffset(0.dp, 3.dp)))
            .dropShadow(shape, Shadow(radius = 0.dp, color = Color.Black.copy(alpha = 0.14f), offset = DpOffset(0.dp, 2.dp)))
            .dropShadow(shape, Shadow(radius = 0.dp, color = Color.Black.copy(alpha = 0.18f), offset = DpOffset(0.dp, 1.dp)))
            .clip(shape)
            .background(FluentColors.Coral)
            .innerShadow(shape, Shadow(radius = 0.dp, color = Color.White.copy(alpha = 0.45f), offset = DpOffset(0.dp, 1.dp)))
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.Center,
    ) { Text(version, color = FluentColors.OnCoral, style = style ?: type.code.copy(fontSize = type.body.fontSize, fontWeight = FontWeight.SemiBold), maxLines = 1) }
}

/** A command on a terminal's fill, with a green prompt, ready to copy. */
@Composable
fun CommandField(
    command: String,
    copyLabel: String,
    modifier: Modifier = Modifier,
    onCopy: () -> Unit,
) {
    val type = LocalFluentType.current
    Row(
        modifier
            .heightIn(min = 44.dp)
            .clip(Corners.Control)
            .background(FluentColors.TerminalFill)
            .border(1.dp, Color.White.copy(alpha = 0.1f), Corners.Control)
            .padding(start = 16.dp, end = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(">", color = FluentColors.Mint, style = type.code.copy(fontWeight = FontWeight.SemiBold))
        Text(command, color = FluentColors.TerminalInk, style = type.code, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        FluentIconButton(Glyph.Copy, copyLabel, tint = FluentColors.TerminalInk, hover = Color.White.copy(alpha = 0.1f), onClick = onCopy)
    }
}

/** A determinate bar: a hairline track under a 3-pixel fill. */
@Composable
fun ProgressBar(
    fraction: Float,
    modifier: Modifier = Modifier,
) {
    val c = LocalFluent.current
    Box(modifier.fillMaxWidth().height(3.dp), contentAlignment = Alignment.CenterStart) {
        Box(Modifier.fillMaxWidth().height(1.dp).background(c.track))
        Box(
            Modifier
                .fillMaxWidth(fraction.coerceIn(0f, 1f))
                .fillMaxHeight()
                .clip(RoundedCornerShape(2.dp))
                .background(c.accent),
        )
    }
}

/** An indeterminate ring in [tint]. */
@Composable
fun ProgressRing(
    modifier: Modifier = Modifier,
    size: Dp = 16.dp,
    tint: Color = LocalFluent.current.accent,
) {
    val spin by rememberInfiniteTransition().animateFloat(0f, FULL_TURN, infiniteRepeatable(tween(RING_MILLIS, easing = LinearEasing), RepeatMode.Restart))
    Canvas(modifier.size(size).graphicsLayer { rotationZ = spin }) {
        val width = size.toPx() / RING_THICKNESS
        drawArc(tint, 0f, QUARTER * 2, false, Offset(width / 2, width / 2), Size(this.size.width - width, this.size.height - width), style = Stroke(width, cap = StrokeCap.Butt))
    }
}

/** A heading above a group. */
@Composable
fun SectionHead(
    title: String,
    modifier: Modifier = Modifier,
    large: Boolean = false,
    action: @Composable () -> Unit = {},
) {
    val type = LocalFluentType.current
    Row(modifier.fillMaxWidth().padding(bottom = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
        Text(title, color = LocalFluent.current.ink, style = if (large) type.subtitle else type.strong)
        action()
    }
}

/** What a list or page says when it has nothing to show. */
@Composable
fun EmptyNote(
    title: String,
    detail: String? = null,
    modifier: Modifier = Modifier,
) {
    val c = LocalFluent.current
    val type = LocalFluentType.current
    Column(modifier.fillMaxWidth().padding(vertical = 36.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(title, color = c.ink, style = type.strong)
        detail?.let { Text(it, color = c.ink2, style = type.small) }
    }
}

private const val DISABLED = 0.5f
private const val DISABLED_INK = 0.4f
private const val FULL_TURN = 360f
private const val QUARTER = 90f
private const val RING_MILLIS = 900
private const val RING_THICKNESS = 8f
private val SWATCH_SHADOW = Color(14, 40, 56, 46)
