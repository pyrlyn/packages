// The colours are the design's own hex values; a name for each would only repeat it.
@file:Suppress("MagicNumber")

// The controls. Each is a real control: reachable by keyboard, with a focus ring, and named for screen readers by its
// words. They hold no state of their own beyond hover and press; the caller owns what they show.

package pyrlyn.glass

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.FocusInteraction
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp

enum class ButtonKind { Primary, Secondary, Raised, Attention, Danger, DangerFill, Ghost, Hero, OnDark }

private val Capsule = RoundedCornerShape(percent = 50)

/** The ring keyboard focus draws around a control. */
@Composable
private fun Modifier.focusRing(
    source: MutableInteractionSource,
    shape: Shape = Capsule,
): Modifier = if (keyboardFocus(source)) border(2.dp, LocalGlass.current.focus, shape) else this

/** A text field keeps its ring however it took the focus, as the place the typing goes. */
@Composable
private fun Modifier.fieldRing(source: MutableInteractionSource): Modifier {
    val focused by source.collectIsFocusedAsState()
    return if (focused) border(2.dp, LocalGlass.current.focus, Capsule) else this
}

/** Whether [source] holds the focus by the keyboard: a click focuses a control too, and that earns it no ring. */
@Composable
fun keyboardFocus(source: MutableInteractionSource): Boolean {
    val focused by source.collectIsFocusedAsState()
    var clicked by remember(source) { mutableStateOf(false) }
    LaunchedEffect(source) {
        source.interactions.collect {
            if (it is PressInteraction.Press) clicked = true
            if (it is FocusInteraction.Unfocus) clicked = false
        }
    }
    return focused && !clicked
}

private class ButtonLook(
    val ink: Color,
    val fill: Brush,
    val rim: Color? = null,
    val lift: List<Lift> = emptyList(),
    val weight: FontWeight = FontWeight.SemiBold,
)

@Composable
private fun buttonLook(kind: ButtonKind): ButtonLook {
    val glass = LocalGlass.current
    return when (kind) {
        ButtonKind.Primary -> {
            ButtonLook(
                glass.accentOn,
                Brush.verticalGradient(listOf(glass.accentTop, glass.accent)),
                lift = listOf(Lift(0.dp, 1.dp, glass.accentPressed), Lift(14.dp, 6.dp, glass.accent.copy(alpha = BUTTON_GLOW))),
            )
        }

        ButtonKind.Secondary -> {
            ButtonLook(glass.ink, SolidColor(glass.control), glass.hairline, glass.raisedLift)
        }

        ButtonKind.Raised -> {
            ButtonLook(glass.accentInk, Brush.verticalGradient(glass.raised), lift = glass.raisedLift, weight = FontWeight.Bold)
        }

        ButtonKind.Attention -> {
            ButtonLook(glass.attentionOn, SolidColor(glass.attentionFill), lift = listOf(Lift(0.dp, 1.dp, Color(0xFF8A3F04)), Lift(10.dp, 4.dp, Color(0x40C25A06))), weight = FontWeight.Bold)
        }

        ButtonKind.Danger -> {
            ButtonLook(glass.danger, SolidColor(glass.control), glass.danger.copy(alpha = DANGER_RIM))
        }

        ButtonKind.DangerFill -> {
            ButtonLook(Color.White, Brush.verticalGradient(listOf(Color(0xFFD0304A), Color(0xFFA81E34))), lift = listOf(Lift(0.dp, 1.dp, Color(0xFF7A1325)), Lift(14.dp, 6.dp, Color(0x4DA81E34))))
        }

        ButtonKind.Ghost -> {
            ButtonLook(glass.accentInk, SolidColor(Color.Transparent))
        }

        ButtonKind.Hero -> {
            ButtonLook(GlassColors.Deep, Brush.verticalGradient(listOf(Color.White, Color(0xFFE3F3EE))), lift = listOf(Lift(0.dp, 1.dp, Color(0xFFA9CDC2)), Lift(18.dp, 8.dp, Color(0x38000000))))
        }

        ButtonKind.OnDark -> {
            ButtonLook(Color.White, SolidColor(Color(0x1FFFFFFF)), Color(0x4DFFFFFF))
        }
    }
}

@Composable
fun GlassButton(
    label: String,
    kind: ButtonKind,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    large: Boolean = false,
    onClick: () -> Unit,
) {
    val look = buttonLook(kind)
    val source = remember { MutableInteractionSource() }
    val hovered by source.collectIsHoveredAsState()
    val pressed by source.collectIsPressedAsState()
    val rise by animateDpAsState(if (hovered && enabled && !pressed) -1.dp else 0.dp, tween(HOVER_MILLIS))
    val scale by animateFloatAsState(if (pressed) PRESSED else 1f, tween(HOVER_MILLIS))
    var layers =
        modifier
            .graphicsLayer {
                translationY = rise.toPx()
                scaleX = scale
                scaleY = scale
            }.alpha(if (enabled) 1f else DISABLED)
    for (lift in look.lift) layers = layers.dropShadow(Capsule, Shadow(radius = lift.blur, color = lift.color, offset = lift.offset))
    Box(
        layers
            .focusRing(source)
            .clip(Capsule)
            .background(look.fill)
            .then(look.rim?.let { Modifier.border(1.dp, it, Capsule) } ?: Modifier)
            .hoverable(source, enabled)
            .clickable(source, indication = null, enabled = enabled, role = Role.Button, onClick = onClick)
            .heightIn(min = if (large) LARGE_HEIGHT else BUTTON_HEIGHT)
            .widthIn(min = if (kind == ButtonKind.Raised) RAISED_WIDTH else 0.dp)
            .padding(horizontal = if (large) 22.dp else 16.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = look.ink, style = body(if (large) LARGE_LABEL else TextSize.LABEL, look.weight), maxLines = 1)
    }
}

/** A round button with only a glyph: [label] is what a screen reader says. */
@Composable
fun GlassIconButton(
    glyph: Glyph,
    label: String,
    tint: Color = LocalGlass.current.ink,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    val source = remember { MutableInteractionSource() }
    val hovered by source.collectIsHoveredAsState()
    Box(
        Modifier
            .size(ICON_BUTTON)
            .alpha(if (enabled) 1f else DISABLED)
            .focusRing(source, CircleShape)
            .clip(CircleShape)
            .background(if (hovered && enabled) LocalGlass.current.hover else Color.Transparent)
            .hoverable(source, enabled)
            .clickable(source, indication = null, enabled = enabled, role = Role.Button, onClick = onClick)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) { GlyphIcon(glyph, tint, ICON_GLYPH) }
}

/** The capsule of mutually exclusive choices. */
@Composable
fun <T> Segmented(
    options: List<T>,
    selected: T,
    label: (T) -> String,
    optionModifier: (T) -> Modifier = { Modifier },
    onSelect: (T) -> Unit,
) {
    val glass = LocalGlass.current
    Row(Modifier.clip(Capsule).background(glass.fill).padding(3.dp)) {
        for (option in options) {
            val on = option == selected
            val source = remember { MutableInteractionSource() }
            var item = optionModifier(option).heightIn(min = SEGMENT_HEIGHT)
            if (on) {
                for (lift in glass.raisedLift) item = item.dropShadow(Capsule, Shadow(radius = lift.blur, color = lift.color, offset = lift.offset))
                item = item.clip(Capsule).background(Brush.verticalGradient(glass.raised))
            }
            Box(
                item
                    .focusRing(source)
                    .clip(Capsule)
                    .clickable(source, indication = null, role = Role.Tab, onClick = { onSelect(option) })
                    .padding(horizontal = 16.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(label(option), color = if (on) glass.ink else glass.ink2, style = body(TextSize.LABEL, if (on) FontWeight.SemiBold else FontWeight.Medium))
            }
        }
    }
}

/** The capsule search field, with the [shortcut] that reaches it when it has one. */
@Composable
fun SearchField(
    value: String,
    placeholder: String,
    modifier: Modifier = Modifier,
    shortcut: String? = null,
    focus: FocusRequester = remember { FocusRequester() },
    onChange: (String) -> Unit,
) {
    val glass = LocalGlass.current
    val source = remember { MutableInteractionSource() }
    BasicTextField(
        value = value,
        onValueChange = onChange,
        singleLine = true,
        textStyle = body(TextSize.BODY).copy(color = glass.ink),
        cursorBrush = SolidColor(glass.accent),
        interactionSource = source,
        modifier = modifier.widthIn(min = 200.dp, max = 320.dp).focusRequester(focus).fieldRing(source),
        decorationBox = { field ->
            Row(
                Modifier
                    .height(SEARCH_HEIGHT)
                    .clip(Capsule)
                    .background(glass.pill)
                    .border(1.dp, glass.pillRim, Capsule)
                    .padding(start = 14.dp, end = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                GlyphIcon(Glyph.Search, glass.ink2, ICON_GLYPH)
                Box(Modifier.weight(1f)) {
                    if (value.isEmpty()) Text(placeholder, color = glass.ink2, style = body(TextSize.BODY), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    field()
                }
                shortcut?.let {
                    Text(
                        it,
                        color = glass.ink2,
                        style = mono(KEY),
                        modifier = Modifier.clip(RoundedCornerShape(6.dp)).background(glass.fill).padding(horizontal = 6.dp, vertical = 3.dp),
                    )
                }
            }
        },
    )
}

enum class BadgeKind { Success, Attention, Progress, Warning, Danger, Neutral }

@Composable
fun Badge(
    label: String,
    kind: BadgeKind,
    modifier: Modifier = Modifier,
) {
    val glass = LocalGlass.current
    val (ink, fill) =
        when (kind) {
            BadgeKind.Success -> glass.success to glass.successSubtle
            BadgeKind.Attention -> glass.attention to glass.attentionSubtle
            BadgeKind.Progress -> glass.progress to glass.progressSubtle
            BadgeKind.Warning -> glass.warning to glass.warningSubtle
            BadgeKind.Danger -> glass.danger to glass.dangerSubtle
            BadgeKind.Neutral -> glass.ink2 to glass.fill
        }
    Box(
        modifier
            .heightIn(min = BADGE_HEIGHT)
            .clip(Capsule)
            .background(fill)
            .padding(horizontal = 9.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = ink, style = body(TextSize.CAPTION, FontWeight.Bold), maxLines = 1)
    }
}

/** A quiet monospaced fact. */
@Composable
fun FactChip(
    label: String,
    modifier: Modifier = Modifier,
) {
    val glass = LocalGlass.current
    Text(label, color = glass.ink2, style = mono(CHIP), modifier = modifier.clip(Capsule).background(glass.fill).padding(horizontal = 10.dp, vertical = 4.dp))
}

/** The coral luggage tag, tilted [tilt] degrees. */
@Composable
fun Tag(
    label: String,
    modifier: Modifier = Modifier,
    tilt: Float = 0f,
) {
    val shape = RoundedCornerShape(TAG_RADIUS)
    Row(
        modifier
            .rotate(tilt)
            .dropShadow(shape, Shadow(radius = 24.dp, color = Color(0x59781E14), offset = DpOffset(0.dp, 14.dp)))
            .clip(shape)
            .background(Brush.verticalGradient(listOf(Color(0xFFFF8A7D), GlassColors.Coral, Color(0xFFE85546))))
            .padding(start = 10.dp, end = 14.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(
            Modifier
                .size(TAG_DOT)
                .clip(CircleShape)
                .background(GlassColors.Mint)
                .padding(3.dp)
                .clip(CircleShape)
                .background(GlassColors.Deep),
        )
        Text(label, color = GlassColors.Deep, style = mono(TAG_TEXT, FontWeight.Bold))
    }
}

/** A command in a terminal's colours, on [fill] when it sits on a light pane. [onCopy] puts it on the clipboard. */
@Composable
fun CommandField(
    command: String,
    copyLabel: String,
    modifier: Modifier = Modifier,
    fill: Color = Color(0x70041418),
    onCopy: () -> Unit,
) {
    val shape = RoundedCornerShape(COMMAND_RADIUS)
    Row(
        modifier
            .clip(shape)
            .background(fill)
            .border(1.dp, Color(0x29FFFFFF), shape)
            .padding(start = 16.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text("$", color = GlassColors.Mint, style = mono(COMMAND))
        Text(command, color = GlassColors.TerminalInk, style = mono(COMMAND), maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        GlassIconButton(Glyph.Copy, copyLabel, GlassColors.TerminalInk, onClick = onCopy)
    }
}

@Composable
fun Progress(
    fraction: Float,
    modifier: Modifier = Modifier,
) {
    val glass = LocalGlass.current
    Box(
        modifier
            .fillMaxWidth()
            .height(PROGRESS_HEIGHT)
            .clip(Capsule)
            .background(glass.track),
    ) {
        Box(
            Modifier
                .fillMaxHeight()
                .fillMaxWidth(fraction.coerceIn(0f, 1f))
                .clip(Capsule)
                .background(Brush.horizontalGradient(listOf(glass.accent, glass.accentTop))),
        )
    }
}

@Composable
fun Spinner() {
    val glass = LocalGlass.current
    CircularProgressIndicator(Modifier.size(SPINNER), color = glass.progress, trackColor = glass.progressSubtle, strokeWidth = SPINNER_STROKE)
}

/** One setting: its label on the left, its control on the right, a hairline under it. */
@Composable
fun FieldRow(
    label: String,
    note: String? = null,
    last: Boolean = false,
    control: @Composable RowScope.() -> Unit,
) {
    val glass = LocalGlass.current
    Column {
        Row(Modifier.fillMaxWidth().heightIn(min = FIELD_HEIGHT).padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(label, color = glass.ink, style = body(TextSize.BODY))
                note?.let { Text(it, color = glass.ink2, style = body(CHIP.toFloat())) }
            }
            control()
        }
        if (!last) Box(Modifier.fillMaxWidth().height(1.dp).background(glass.separator))
    }
}

/** [label] is what a screen reader says, and the row it sits in shows it too. */
@Composable
fun GlassSwitch(
    on: Boolean,
    label: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onChange: (Boolean) -> Unit,
) {
    val glass = LocalGlass.current
    val travel by animateDpAsState(if (on) SWITCH_TRAVEL else 0.dp, tween(SWITCH_MILLIS))
    Box(
        modifier
            .size(SWITCH_WIDTH, SWITCH_HEIGHT)
            .alpha(if (enabled) 1f else DISABLED)
            .clip(Capsule)
            .background(if (on) glass.accent else glass.track)
            .toggleable(on, enabled = enabled, role = Role.Switch, onValueChange = onChange)
            .semantics { contentDescription = label },
    ) {
        Box(
            Modifier
                .offset(x = travel)
                .padding(2.dp)
                .size(SWITCH_KNOB)
                .dropShadow(CircleShape, Shadow(radius = 4.dp, color = Color(0x400E2838), offset = DpOffset(0.dp, 2.dp)))
                .clip(CircleShape)
                .background(Brush.verticalGradient(listOf(Color.White, Color(0xFFF2F5F8)))),
        )
    }
}

/** A popup choice in a capsule. */
@Composable
fun <T> Select(
    options: List<T>,
    selected: T,
    label: (T) -> String,
    modifier: Modifier = Modifier,
    optionModifier: (T) -> Modifier = { Modifier },
    onSelect: (T) -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    Box {
        GlassButton(label(selected), ButtonKind.Secondary, modifier) { open = true }
        DropdownMenu(open, onDismissRequest = { open = false }) {
            for (option in options) {
                DropdownMenuItem(
                    text = { Text(label(option), style = body(TextSize.LABEL)) },
                    modifier = optionModifier(option),
                    onClick = {
                        open = false
                        onSelect(option)
                    },
                )
            }
        }
    }
}

/** A thin track in the accent colour up to a white thumb, as a Mac's range control. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GlassSlider(
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    label: String,
    modifier: Modifier = Modifier,
    onChange: (Float) -> Unit,
) {
    val glass = LocalGlass.current
    Slider(
        value = value,
        onValueChange = onChange,
        modifier = modifier.semantics { contentDescription = label },
        valueRange = range,
        thumb = {
            Box(Modifier.size(SLIDER_THUMB).dropShadow(CircleShape, Shadow(3.dp, Color.Black.copy(alpha = THUMB_SHADOW), offset = DpOffset(0.dp, 1.dp))).background(Color.White, CircleShape))
        },
        track = { state ->
            val span = state.valueRange.endInclusive - state.valueRange.start
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(SLIDER_TRACK)
                    .clip(Capsule)
                    .background(glass.track),
            ) {
                Box(Modifier.fillMaxWidth((state.value - state.valueRange.start) / span).fillMaxHeight().background(glass.accent))
            }
        },
    )
}

/** One colour to choose, a ring on the chosen one; no [color] draws the neutral "none" swatch. [label] names it aloud. */
@Composable
fun Swatch(
    color: Color?,
    label: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val glass = LocalGlass.current
    val source = remember { MutableInteractionSource() }
    val fill = color?.let { SolidColor(it) } ?: Brush.sweepGradient(listOf(Color.White, Color(0xFFDFE6EE), Color.White, Color(0xFFDFE6EE), Color.White))
    Box(
        modifier
            .size(SWATCH_RING)
            .then(if (selected) Modifier.border(2.dp, glass.accent, CircleShape) else Modifier)
            .focusRing(source, CircleShape)
            .padding(4.dp)
            .clip(CircleShape)
            .background(fill)
            .selectable(selected, source, indication = null, role = Role.RadioButton, onClick = onClick)
            .semantics { contentDescription = label },
    )
}

/** A heading in the page's flow. */
@Composable
fun SectionTitle(
    title: String,
    modifier: Modifier = Modifier,
    small: Boolean = false,
) {
    val style = if (small) body(TextSize.HEADING, FontWeight.SemiBold) else display(SECTION)
    Text(title, color = LocalGlass.current.ink, style = style, modifier = modifier.semantics { heading() })
}

/** The first line of an empty screen; [detail] says what to do about it. */
@Composable
fun EmptyNote(
    title: String,
    detail: String? = null,
) {
    val glass = LocalGlass.current
    Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 56.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(title, color = glass.ink, style = body(EMPTY, FontWeight.SemiBold))
        detail?.let { Text(it, color = glass.ink2, style = body(TextSize.BODY)) }
    }
}

private const val LARGE_LABEL = 15f
private const val EMPTY = 17f
private const val SECTION = 20
private const val CHIP = 12
private const val KEY = 11
private const val TAG_TEXT = 14
private const val COMMAND = 14
private const val PRESSED = 0.98f
private const val DISABLED = 0.5f
private const val BUTTON_GLOW = 0.3f
private const val THUMB_SHADOW = 0.3f
private const val DANGER_RIM = 0.15f
private const val HOVER_MILLIS = 180
private const val SWITCH_MILLIS = 200
private val BUTTON_HEIGHT = 32.dp
private val LARGE_HEIGHT = 44.dp
private val RAISED_WIDTH = 64.dp
private val SEGMENT_HEIGHT = 30.dp
private val SEARCH_HEIGHT = 36.dp
private val BADGE_HEIGHT = 22.dp
private val ICON_BUTTON = 36.dp
private val ICON_GLYPH = 16.dp
private val TAG_RADIUS = 11.dp
private val TAG_DOT = 10.dp
private val COMMAND_RADIUS = 14.dp
private val PROGRESS_HEIGHT = 6.dp
private val SPINNER = 22.dp
private val SLIDER_THUMB = 18.dp
private val SLIDER_TRACK = 4.dp
private val SPINNER_STROKE = 2.5.dp
private val FIELD_HEIGHT = 52.dp
private val SWITCH_WIDTH = 42.dp
private val SWITCH_HEIGHT = 26.dp
private val SWITCH_KNOB = 22.dp
private val SWITCH_TRAVEL = 16.dp
private val SWATCH_RING = 34.dp
