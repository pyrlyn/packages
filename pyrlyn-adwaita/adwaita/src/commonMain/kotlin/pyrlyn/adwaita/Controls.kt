// The sizes are the design system's own values; a name for each would only repeat it.
@file:Suppress("MagicNumber")

package pyrlyn.adwaita

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import pyrlyn.glass.Glyph
import pyrlyn.glass.GlyphIcon
import pyrlyn.glass.keyboardFocus

private const val DISABLED = 0.45f
private val Capsule = RoundedCornerShape(50)

/** The focus ring, shown for the keyboard only. */
@Composable
internal fun Modifier.focusRing(
    source: MutableInteractionSource,
    shape: Shape,
): Modifier = if (keyboardFocus(source)) border(2.dp, LocalAdwaita.current.accentText, shape) else this

/** A square icon button of the header bar. [label] names it for screen readers. */
@Composable
fun FlatButton(
    glyph: Glyph,
    label: String,
    modifier: Modifier = Modifier,
    size: Dp = 36.dp,
    on: Boolean = false,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    val c = LocalAdwaita.current
    val source = remember { MutableInteractionSource() }
    val hovered by source.collectIsHoveredAsState()
    val fill =
        when {
            on -> c.fill3
            enabled && hovered -> c.hover
            else -> Color.Transparent
        }
    Box(
        modifier
            .size(size)
            .graphicsLayer { alpha = if (enabled) 1f else DISABLED }
            .clip(Corners.Small)
            .background(fill)
            .focusRing(source, Corners.Small)
            .hoverable(source)
            .clickable(source, null, enabled, label, Role.Button, onClick)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        GlyphIcon(glyph, c.ink, size / 2)
    }
}

/** What a pill button does, by how much it should stand out. */
enum class PillStyle { Standard, Accent, Update, Danger, Flat, FlatDanger }

/** A pill's three heights. */
enum class PillSize(
    val height: Int,
    val pad: Int,
    val text: Int,
) {
    Small(30, 12, 12),
    Normal(36, 18, 13),
    Large(44, 24, 14),
}

private class PillLook(
    val fill: Color,
    val hover: Color,
    val ink: Color,
)

@Composable
private fun PillStyle.look(): PillLook {
    val c = LocalAdwaita.current
    return when (this) {
        PillStyle.Standard -> PillLook(c.fill2, c.fill2.copy(alpha = 0.2f), c.ink)
        PillStyle.Accent -> PillLook(c.accent, c.accentHover, c.accentInk)
        PillStyle.Update -> PillLook(c.update, c.updateHover, c.updateInk)
        PillStyle.Danger -> PillLook(c.danger, c.dangerHover, Color.White)
        PillStyle.Flat -> PillLook(Color.Transparent, c.fill1, c.ink)
        PillStyle.FlatDanger -> PillLook(Color.Transparent, Color(255, 120, 110, 31), c.dangerText)
    }
}

/** The design's button. [glyph] leads the text; [busy] turns it into a disabled button with a spinner. */
@Composable
fun PillButton(
    text: String,
    modifier: Modifier = Modifier,
    style: PillStyle = PillStyle.Standard,
    size: PillSize = PillSize.Normal,
    glyph: Glyph? = null,
    trailingGlyph: Glyph? = null,
    enabled: Boolean = true,
    busy: Boolean = false,
    onClick: () -> Unit,
) {
    val type = LocalAdwaitaType.current
    val look = style.look()
    val source = remember { MutableInteractionSource() }
    val hovered by source.collectIsHoveredAsState()
    val live = enabled && !busy
    Row(
        modifier
            .graphicsLayer { alpha = if (enabled || busy) 1f else DISABLED }
            .heightIn(min = size.height.dp)
            .then(if (style == PillStyle.Accent) Modifier.accentLift() else Modifier)
            .clip(Capsule)
            .background(if (live && hovered) look.hover else look.fill)
            .focusRing(source, Capsule)
            .hoverable(source)
            .clickable(source, null, live, role = Role.Button, onClick = onClick)
            .padding(horizontal = size.pad.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
    ) {
        if (busy) Spinner(look.ink) else glyph?.let { GlyphIcon(it, look.ink, (size.text + 3).dp) }
        Text(text, color = look.ink, style = type.captionStrong.copy(fontSize = size.text.sp), maxLines = 1)
        trailingGlyph?.let { GlyphIcon(it, look.ink, (size.text + 3).dp) }
    }
}

private fun Modifier.accentLift() =
    dropShadow(Capsule, Shadow(radius = 18.dp, color = Color(30, 180, 140, 77), offset = DpOffset(0.dp, 8.dp)))
        .innerShadow(Capsule, Shadow(radius = 0.dp, color = Color.White.copy(alpha = 0.5f), offset = DpOffset(0.dp, 1.dp)))

/** A small count on a pill of its own; [update] gives it the update colour. */
@Composable
fun DotBadge(
    text: String,
    modifier: Modifier = Modifier,
    update: Boolean = false,
) {
    val c = LocalAdwaita.current
    Box(
        modifier
            .heightIn(min = 18.dp)
            .widthIn(min = 18.dp)
            .clip(RoundedCornerShape(9.dp))
            .background(if (update) c.update else c.accent)
            .padding(horizontal = 5.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = if (update) c.updateInk else c.accentInk, style = LocalAdwaitaType.current.badge)
    }
}

/** One row of the view switcher. */
class SwitcherItem<T>(
    val value: T,
    val title: String,
    val glyph: Glyph,
    val badge: String? = null,
    val modifier: Modifier = Modifier,
)

/** The header bar's views. */
@Composable
fun <T> ViewSwitcher(
    items: List<SwitcherItem<T>>,
    selected: T?,
    modifier: Modifier = Modifier,
    onPick: (T) -> Unit,
) {
    Row(modifier.selectableGroup(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        for (item in items) SwitcherEntry(item, item.value == selected) { onPick(item.value) }
    }
}

@Composable
private fun <T> SwitcherEntry(
    item: SwitcherItem<T>,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val c = LocalAdwaita.current
    val source = remember { MutableInteractionSource() }
    val hovered by source.collectIsHoveredAsState()
    val ink = if (selected || hovered) c.ink else c.ink2
    val fill =
        when {
            selected -> c.selected
            hovered -> c.fill1
            else -> Color.Transparent
        }
    Row(
        item.modifier
            .heightIn(min = 36.dp)
            .clip(Corners.Small)
            .background(fill)
            .focusRing(source, Corners.Small)
            .hoverable(source)
            .selectable(selected, source, null, role = Role.Tab, onClick = onClick)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        GlyphIcon(item.glyph, ink, 16.dp)
        Text(item.title, color = ink, style = LocalAdwaitaType.current.bodyStrong, maxLines = 1)
        item.badge?.let { DotBadge(it) }
    }
}

/** Filter chips, of which one is on. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun <T> Chips(
    options: List<T>,
    selected: T,
    label: (T) -> String,
    modifier: Modifier = Modifier,
    optionModifier: (T) -> Modifier = { Modifier },
    onPick: (T) -> Unit,
) {
    FlowRow(modifier.selectableGroup(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        for (option in options) Chip(label(option), option == selected, optionModifier(option)) { onPick(option) }
    }
}

@Composable
private fun Chip(
    text: String,
    selected: Boolean,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    val c = LocalAdwaita.current
    val source = remember { MutableInteractionSource() }
    val hovered by source.collectIsHoveredAsState()
    val fill =
        when {
            selected -> c.accent
            hovered -> c.fill2.copy(alpha = 0.15f)
            else -> c.fill1.copy(alpha = 0.09f)
        }
    Box(
        modifier
            .heightIn(min = 34.dp)
            .then(if (selected) Modifier.dropShadow(Capsule, Shadow(radius = 14.dp, color = Color(30, 180, 140, 77), offset = DpOffset(0.dp, 6.dp))) else Modifier)
            .clip(Capsule)
            .background(fill)
            .focusRing(source, Capsule)
            .hoverable(source)
            .selectable(selected, source, null, role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = if (selected) c.accentInk else c.ink, style = LocalAdwaitaType.current.captionStrong, maxLines = 1)
    }
}

/** A row of joined buttons of which one is on, for a page's tabs. */
@Composable
fun <T> LinkedToggle(
    options: List<T>,
    selected: T,
    label: (T) -> String,
    modifier: Modifier = Modifier,
    optionModifier: (T) -> Modifier = { Modifier },
    onPick: (T) -> Unit,
) {
    val c = LocalAdwaita.current
    val shape = RoundedCornerShape(11.dp)
    Row(
        modifier
            .clip(shape)
            .background(c.well)
            .border(1.dp, c.line, shape)
            .padding(3.dp)
            .selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        for (option in options) {
            val on = option == selected
            val source = remember { MutableInteractionSource() }
            Box(
                optionModifier(option)
                    .heightIn(min = 32.dp)
                    .clip(Corners.Small)
                    .background(if (on) c.fill3 else Color.Transparent)
                    .then(if (on) Modifier.innerShadow(Corners.Small, Shadow(radius = 0.dp, color = Color.White.copy(alpha = 0.2f), offset = DpOffset(0.dp, 1.dp))) else Modifier)
                    .focusRing(source, Corners.Small)
                    .selectable(on, source, null, role = Role.Tab) { onPick(option) }
                    .padding(horizontal = 16.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(label(option), color = if (on) c.ink else c.ink2, style = LocalAdwaitaType.current.captionStrong, maxLines = 1)
            }
        }
    }
}

/** On or off. Named by [label], which the row around it shows. */
@Composable
fun ToggleSwitch(
    checked: Boolean,
    label: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onChange: (Boolean) -> Unit,
) {
    val c = LocalAdwaita.current
    val source = remember { MutableInteractionSource() }
    val shape = RoundedCornerShape(13.dp)
    val travel by animateDpAsState(if (checked) 23.dp else 3.dp, tween(TOGGLE_MILLIS))
    Box(
        modifier
            .size(46.dp, 26.dp)
            .graphicsLayer { alpha = if (enabled) 1f else DISABLED }
            .clip(shape)
            .background(if (checked) c.accent else c.fill3)
            .focusRing(source, shape)
            .toggleable(checked, source, null, enabled, Role.Switch) { onChange(it) }
            .semantics { contentDescription = label },
        contentAlignment = Alignment.CenterStart,
    ) {
        Box(
            Modifier
                .offset(x = travel)
                .size(20.dp)
                .dropShadow(CircleShape, Shadow(radius = 4.dp, color = Color.Black.copy(alpha = 0.35f), offset = DpOffset(0.dp, 2.dp)))
                .background(Color.White, CircleShape),
        )
    }
}

/** A text entry; with [glyph], its icon leads. [focus] takes the keyboard when the app asks for it. */
@Composable
fun Entry(
    value: String,
    placeholder: String,
    modifier: Modifier = Modifier,
    glyph: Glyph? = null,
    focus: FocusRequester? = null,
    onChange: (String) -> Unit,
) {
    val c = LocalAdwaita.current
    val type = LocalAdwaitaType.current
    val source = remember { MutableInteractionSource() }
    val focused by source.collectIsFocusedAsState()
    val state = rememberTextFieldState(value)
    LaunchedEffect(value) { if (value != state.text.toString()) state.setTextAndPlaceCursorAtEnd(value) }
    LaunchedEffect(state) { snapshotFlow { state.text.toString() }.collect { if (it != value) onChange(it) } }
    BasicTextField(
        state,
        modifier = (focus?.let { modifier.focusRequester(it) } ?: modifier).height(36.dp).semantics { contentDescription = placeholder },
        interactionSource = source,
        textStyle = type.caption.copy(color = c.ink),
        cursorBrush = SolidColor(c.accent),
        lineLimits = TextFieldLineLimits.SingleLine,
        decorator = { field ->
            Row(
                Modifier
                    .fillMaxHeight()
                    .clip(Corners.Small)
                    .background(c.well)
                    .innerShadow(Corners.Small, Shadow(radius = 6.dp, color = Color.Black.copy(alpha = 0.25f), offset = DpOffset(0.dp, 2.dp)))
                    .border(1.dp, if (focused) c.accent else c.line, Corners.Small)
                    .padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                glyph?.let { GlyphIcon(it, c.ink2, 16.dp) }
                Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                    if (state.text.isEmpty()) Text(placeholder, color = c.ink2, style = type.caption, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    field()
                }
            }
        },
    )
}

/** What a status pill reports. */
enum class Status { Neutral, Ok, Update, Warning, Danger }

@Composable
private fun Status.colors(): Pair<Color, Color> {
    val c = LocalAdwaita.current
    return when (this) {
        Status.Neutral -> c.fill2 to c.ink
        Status.Ok -> c.accent.copy(alpha = 0.18f) to c.accentText
        Status.Update -> c.update to c.updateInk
        Status.Warning -> c.warning to c.warningInk
        Status.Danger -> Color(255, 120, 110, 41) to c.dangerText
    }
}

/** A short state, on a pill. */
@Composable
fun StatusPill(
    text: String,
    status: Status = Status.Neutral,
    modifier: Modifier = Modifier,
    glyph: Glyph? = null,
) {
    val (fill, ink) = status.colors()
    Row(
        modifier
            .heightIn(min = 22.dp)
            .clip(Capsule)
            .background(fill)
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        glyph?.let { GlyphIcon(it, ink, 12.dp) }
        Text(text, color = ink, style = LocalAdwaitaType.current.small, maxLines = 1)
    }
}

/** A plain confirmation with a tick, where the design puts no pill. */
@Composable
fun OkLabel(
    text: String,
    modifier: Modifier = Modifier,
) {
    val c = LocalAdwaita.current
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        GlyphIcon(Glyph.Check, c.accentText, 14.dp)
        Text(text, color = c.accentText, style = LocalAdwaitaType.current.captionStrong, maxLines = 1)
    }
}

/** A lit dot: a source's colour, a day's kind. */
@Composable
fun GlowDot(
    color: Color,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier
            .size(10.dp)
            .dropShadow(CircleShape, Shadow(radius = 8.dp, color = color))
            .background(color, CircleShape),
    )
}

/** Something is under way, with no telling how far it has come. */
@Composable
fun Spinner(
    color: Color,
    modifier: Modifier = Modifier,
) {
    val turn by rememberInfiniteTransition().animateFloat(0f, 360f, infiniteRepeatable(tween(SPIN_MILLIS, easing = LinearEasing), RepeatMode.Restart))
    Canvas(modifier.size(14.dp)) {
        val width = 2.dp.toPx()
        drawArc(color, turn, 270f, false, Offset(width / 2, width / 2), Size(size.width - width, size.height - width), style = Stroke(width, cap = StrokeCap.Round))
    }
}

/** How far something has come, between 0 and 1. */
@Composable
fun ProgressBar(
    fraction: Float,
    modifier: Modifier = Modifier,
) {
    val c = LocalAdwaita.current
    val shape = RoundedCornerShape(3.dp)
    val done = fraction.coerceIn(0f, 1f)
    Box(
        modifier
            .fillMaxWidth()
            .height(6.dp)
            .clip(shape)
            .background(c.fill2)
            .semantics { progressBarRangeInfo = ProgressBarRangeInfo(done, 0f..1f) },
    ) {
        Box(
            Modifier
                .fillMaxWidth(done)
                .fillMaxHeight()
                .dropShadow(shape, Shadow(radius = 10.dp, color = c.accent.copy(alpha = 0.6f)))
                .background(Brush.horizontalGradient(listOf(c.accent, c.accentText)), shape),
        )
    }
}

/** A command to copy, after a green prompt. */
@Composable
fun CommandLine(
    command: String,
    copyLabel: String,
    modifier: Modifier = Modifier,
    onCopy: () -> Unit,
) {
    val c = LocalAdwaita.current
    val line =
        remember(command, c.accent) {
            buildAnnotatedString {
                withStyle(SpanStyle(color = c.accent, fontWeight = FontWeight.Bold)) { append("$ ") }
                append(command)
            }
        }
    Row(
        modifier
            .height(40.dp)
            .clip(Corners.Small)
            .background(c.terminal.copy(alpha = 0.35f))
            .padding(start = 14.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(line, color = c.ink, style = LocalAdwaitaType.current.mono, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        FlatButton(Glyph.Copy, copyLabel, size = 32.dp, onClick = onCopy)
    }
}

/** One line of a [Terminal]: what to dim or colour is the caller's. */
class TerminalLine(
    val text: AnnotatedString,
) {
    constructor(text: String) : this(AnnotatedString(text))
}

/** Text a command printed. */
@Composable
fun Terminal(
    lines: List<TerminalLine>,
    modifier: Modifier = Modifier,
) {
    val c = LocalAdwaita.current
    Column(
        modifier
            .fillMaxWidth()
            .clip(Corners.Medium)
            .background(c.terminal)
            .border(1.dp, c.line, Corners.Medium)
            .padding(horizontal = 18.dp, vertical = 16.dp),
    ) {
        for (line in lines) Text(line.text, color = c.terminalInk, style = LocalAdwaitaType.current.terminal)
    }
}

/** Names and values, side by side; a value in the mono face, as the design sets paths. */
@Composable
fun Facts(
    facts: List<Pair<String, String>>,
    modifier: Modifier = Modifier,
) {
    val c = LocalAdwaita.current
    val type = LocalAdwaitaType.current
    Column(modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        for ((name, value) in facts) {
            Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                Text(name, color = c.ink2, style = type.caption, modifier = Modifier.widthIn(min = FACT_NAME))
                Text(value, color = c.ink, style = type.mono.copy(fontWeight = FontWeight.Normal), modifier = Modifier.weight(1f))
            }
        }
    }
}

/** A heading the screen reader can jump to. */
@Composable
fun Heading(
    text: String,
    modifier: Modifier = Modifier,
) {
    Text(text, color = LocalAdwaita.current.ink, style = LocalAdwaitaType.current.heading, modifier = modifier.semantics { heading() })
}

/** A text link. */
@Composable
fun TextLink(
    text: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val c = LocalAdwaita.current
    val source = remember { MutableInteractionSource() }
    val hovered by source.collectIsHoveredAsState()
    Text(
        text,
        color = if (hovered) c.linkHover else c.accentText,
        style = LocalAdwaitaType.current.captionStrong,
        modifier =
            modifier
                .focusRing(source, Corners.Small)
                .hoverable(source)
                .clickable(source, null, role = Role.Button, onClick = onClick),
    )
}

private val FACT_NAME = 96.dp
private const val TOGGLE_MILLIS = 150
private const val SPIN_MILLIS = 800
