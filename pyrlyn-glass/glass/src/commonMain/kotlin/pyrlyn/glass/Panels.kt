// The colours are the design's own hex values; a name for each would only repeat it.
@file:Suppress("MagicNumber")

// Panes with a set arrangement: the modal sheet, the floating status bar, list rows, cards and the hero. They take
// their words and actions as parameters and know nothing of what those mean.

package pyrlyn.glass

import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

/** A pane under the toolbar over a scrim. A dialog carries it, so it traps focus and covers the whole window. */
@Composable
fun Sheet(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, scrimColor = LocalGlass.current.scrim),
    ) {
        Box(Modifier.fillMaxSize().pointerInput(onDismiss) { detectTapGestures { onDismiss() } }.padding(top = SHEET_TOP), contentAlignment = Alignment.TopCenter) {
            Column(
                modifier
                    .padding(horizontal = 16.dp)
                    .widthIn(max = SHEET_WIDTH)
                    .fillMaxWidth()
                    .glass(Pane.Sheet)
                    // A press on the sheet is not a press on the scrim.
                    .pointerInput(Unit) { detectTapGestures {} }
                    .padding(28.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
                content = content,
            )
        }
    }
}

@Composable
fun SheetTitle(title: String) = Text(title, color = LocalGlass.current.ink, style = display(SHEET_TITLE, FontWeight.SemiBold))

@Composable
fun SheetText(words: String) = Text(words, color = LocalGlass.current.ink2, style = body(TextSize.LABEL))

/** The sheet's buttons, at its end. */
@Composable
fun SheetActions(buttons: @Composable RowScope.() -> Unit) {
    Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.End), content = buttons)
}

/** The bar over the content: what is happening, how far along, and what the person can do about it. */
@Composable
fun StatusBar(
    title: String,
    message: String?,
    fraction: Float?,
    modifier: Modifier = Modifier,
    leading: @Composable () -> Unit,
    action: @Composable () -> Unit,
) {
    val glass = LocalGlass.current
    Row(
        modifier
            .fillMaxWidth()
            .glass(Pane.Floating)
            .padding(start = 16.dp, end = 10.dp, top = 10.dp, bottom = 10.dp)
            .semantics { liveRegion = LiveRegionMode.Polite },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        leading()
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(title, color = glass.ink, style = body(TextSize.LABEL, FontWeight.SemiBold), maxLines = 1)
                message?.let { Text(it, color = glass.ink2, style = body(TextSize.LABEL), maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false)) }
                Spacer(Modifier.weight(1f))
                fraction?.let { Text("${(it * PERCENT).toInt()}%", color = glass.ink2, style = mono(CAPTION_MONO)) }
            }
            fraction?.let { Progress(it) }
        }
        action()
    }
}

/** One thing in a list: [icon], its [title] with [badge] beside it, [meta] and [detail] under them, [actions] at the end. */
@Composable
fun ListRow(
    title: String,
    meta: String,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    icon: @Composable () -> Unit,
    badge: @Composable RowScope.() -> Unit = {},
    detail: @Composable ColumnScope.() -> Unit = {},
    actions: @Composable RowScope.() -> Unit = {},
) {
    val glass = LocalGlass.current
    val source = remember { MutableInteractionSource() }
    val hovered by source.collectIsHoveredAsState()
    Row(
        modifier
            .fillMaxWidth()
            .graphicsLayer { translationY = if (hovered) -ROW_RISE else 0f }
            .glass(Pane.Row)
            .hoverable(source)
            .then(onClick?.let { Modifier.clickable(source, indication = null, onClick = it) } ?: Modifier)
            .heightIn(min = ROW_HEIGHT)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        icon()
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(title, color = glass.ink, style = body(ROW_TITLE, FontWeight.SemiBold), maxLines = 1)
                badge()
            }
            Text(meta, color = glass.ink2, style = mono(CHIP_MONO), maxLines = 1, overflow = TextOverflow.Ellipsis)
            detail()
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp), content = actions)
    }
}

/** A tilting card in a grid: [icon], [title], a monospaced [meta], a [description] and [action] at the end. */
@Composable
fun Tile(
    title: String,
    meta: String?,
    description: String?,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    icon: @Composable () -> Unit,
    action: @Composable () -> Unit = {},
) {
    val glass = LocalGlass.current
    val source = remember { MutableInteractionSource() }
    Row(
        modifier
            .fillMaxWidth()
            .tilt(source)
            .glass(Pane.Tile)
            .then(onClick?.let { Modifier.clickable(source, indication = null, onClick = it) } ?: Modifier)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        icon()
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(title, color = glass.ink, style = body(TextSize.HEADING, FontWeight.SemiBold), maxLines = 1, overflow = TextOverflow.Ellipsis)
            meta?.let { Text(it, color = glass.ink2, style = mono(CHIP_MONO), maxLines = 1, overflow = TextOverflow.Ellipsis) }
            description?.let { Text(it, color = glass.ink2, style = body(TILE_TEXT), maxLines = 2, overflow = TextOverflow.Ellipsis) }
        }
        action()
    }
}

/** A flat fact: a [ColorTile] of [top] to [bottom] with [glyph], a title and a line of explanation. */
@Composable
fun FeatureCard(
    title: String,
    text: String,
    top: Color,
    bottom: Color,
    glyph: Glyph,
    modifier: Modifier = Modifier,
) {
    val glass = LocalGlass.current
    Row(modifier.fillMaxSize().glass(Pane.Card).padding(20.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        ColorTile(top, bottom) { GlyphIcon(glyph, Color.White, TILE_GLYPH) }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(title, color = glass.ink, style = body(CARD_TITLE, FontWeight.SemiBold))
            Text(text, color = glass.ink2, style = body(TextSize.BODY))
        }
    }
}

/** A number with its meaning under it. */
@Composable
fun StatCard(
    value: String,
    label: String,
    top: Color,
    bottom: Color,
    glyph: Glyph,
    modifier: Modifier = Modifier,
) {
    val glass = LocalGlass.current
    Row(modifier.fillMaxSize().glass(Pane.Card).padding(20.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        ColorTile(top, bottom) { GlyphIcon(glyph, Color.White, TILE_GLYPH) }
        Column {
            Text(value, color = glass.ink, style = display(STAT))
            Text(label, color = glass.ink2, style = body(TextSize.LABEL))
        }
    }
}

/** Label and value pairs, the labels in a column of [labelWidth]. The values can be selected. */
@Composable
fun Facts(
    rows: List<Pair<String, String>>,
    modifier: Modifier = Modifier,
    labelWidth: Dp = FACT_LABEL,
) {
    val glass = LocalGlass.current
    Column(modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        for ((label, value) in rows) {
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(label, color = glass.ink2, style = body(TextSize.LABEL), modifier = Modifier.widthIn(min = labelWidth, max = labelWidth))
                SelectionContainer { Text(value, color = glass.ink, style = mono(CHIP_MONO)) }
            }
        }
    }
}

/** A statement in the deep gradient with two glows, for the top of a page. */
@Composable
fun HeroPanel(
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit,
) {
    val shape = RoundedCornerShape(HERO_RADIUS)
    Box(modifier.fillMaxWidth().clip(shape).background(Brush.linearGradient(0f to GlassColors.Deep, HERO_MIDDLE to Color(0xFF0D5A6E), 1f to HERO_END, start = Offset.Zero, end = Offset.Infinite))) {
        Canvas(Modifier.matchParentSize()) {
            glow(Color(0x733DDCB0), Offset(size.width * HERO_MINT_X, size.height * HERO_MINT_Y), HERO_MINT_RX, HERO_MINT_RY)
            glow(Color(0x40FF6B5B), Offset(0f, size.height * HERO_CORAL_Y), HERO_CORAL_RX, HERO_CORAL_RY)
        }
        Row(Modifier.padding(40.dp), horizontalArrangement = Arrangement.spacedBy(32.dp), verticalAlignment = Alignment.CenterVertically, content = content)
    }
}

/** The small capsule over a hero's headline. */
@Composable
fun Eyebrow(
    label: String,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(percent = 50)
    Row(
        modifier
            .clip(shape)
            .background(Color(0x24FFFFFF))
            .border(1.dp, Color(0x47FFFFFF), shape)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(
            Modifier
                .size(8.dp)
                .rotate(EYEBROW_TILT)
                .clip(RoundedCornerShape(2.dp))
                .background(GlassColors.Coral),
        )
        Text(label.uppercase(), color = Color.White, style = body(EYEBROW, FontWeight.SemiBold))
    }
}

private const val SHEET_TITLE = 17
private const val PERCENT = 100
private const val CAPTION_MONO = 12
private const val CHIP_MONO = 12
private const val ROW_TITLE = 14f
private const val ROW_RISE = 2f
private const val TILE_TEXT = 13f
private const val CARD_TITLE = 16f
private const val STAT = 28
private const val EYEBROW = 12f
private const val EYEBROW_TILT = -18f
private const val HERO_MIDDLE = 0.52f
private const val HERO_MINT_X = 0.8f
private const val HERO_MINT_Y = -0.1f
private const val HERO_MINT_RX = 1200f
private const val HERO_MINT_RY = 600f
private const val HERO_CORAL_Y = 1.1f
private const val HERO_CORAL_RX = 1000f
private const val HERO_CORAL_RY = 520f
private val HERO_END = Color(0xFF096DBF)
private val HERO_RADIUS = 28.dp
private val SHEET_TOP = 90.dp
private val SHEET_WIDTH = 440.dp
private val ROW_HEIGHT = 64.dp
private val TILE_GLYPH = 24.dp
private val FACT_LABEL = 96.dp
