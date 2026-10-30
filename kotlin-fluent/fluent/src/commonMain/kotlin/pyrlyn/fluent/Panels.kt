// The sizes and alphas are the design system's own values; a name for each would only repeat it.
@file:Suppress("MagicNumber")

// What a page is filled with: panels, rows, tiles, the flyout, the banner. They take words and slots and know nothing of
// what they show.

package pyrlyn.fluent

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.FlowRowScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.draw.innerShadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import pyrlyn.glass.Glyph
import pyrlyn.glass.GlyphIcon

/** A card holding a block of a page. */
@Composable
fun Panel(
    modifier: Modifier = Modifier,
    padding: PaddingValues = PaddingValues(horizontal = 24.dp, vertical = 20.dp),
    spacing: Dp = 16.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(modifier.card().padding(padding), verticalArrangement = Arrangement.spacedBy(spacing), content = content)
}

/** A panel leaning back, for what heads a page; its content learns when the pointer raises it. */
@Composable
fun LiftPanel(
    modifier: Modifier = Modifier,
    padding: Dp = 28.dp,
    content: @Composable ColumnScope.(hovered: Boolean) -> Unit,
) {
    val source = remember { MutableInteractionSource() }
    val hovered by source.collectIsHoveredAsState()
    Column(modifier.lift(source).card(flat = true).padding(padding)) { content(hovered) }
}

/** The gap between the rows of a list. */
val RowGap = 3.dp

/** A row of a list, each row its own card. [leading] learns when the pointer is on the row, to raise its icon. */
@Composable
fun ListRow(
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    leading: @Composable (hovered: Boolean) -> Unit = {},
    actions: @Composable RowScope.() -> Unit = {},
    body: @Composable ColumnScope.() -> Unit,
) {
    val c = LocalFluent.current
    val source = remember { MutableInteractionSource() }
    val hovered by source.collectIsHoveredAsState()
    Box(modifier.fillMaxWidth().hoverable(source), contentAlignment = Alignment.CenterStart) {
        WrappingRow(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 72.dp)
                .card(Corners.Item, if (hovered || selected) c.cardHover else c.card, flat = true)
                .padding(horizontal = 18.dp, vertical = 14.dp),
        ) {
            leading(hovered)
            Column(Modifier.weight(1f).widthIn(min = 260.dp), content = body)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically, content = actions)
        }
        if (selected) Box(Modifier.size(3.dp, 24.dp).clip(RoundedCornerShape(2.dp)).background(c.accent))
    }
}

/** A setting: its icon and words on the left, the control that changes it on the right. */
@Composable
fun SettingsCard(
    title: String,
    modifier: Modifier = Modifier,
    glyph: Glyph? = null,
    note: String? = null,
    control: @Composable () -> Unit = {},
) {
    val c = LocalFluent.current
    val type = LocalFluentType.current
    WrappingRow(
        modifier
            .fillMaxWidth()
            .heightIn(min = 68.dp)
            .card(Corners.Item, flat = true)
            .padding(horizontal = 18.dp, vertical = 14.dp),
    ) {
        glyph?.let { GlyphIcon(it, c.ink, 20.dp) }
        Column(Modifier.weight(1f).widthIn(min = 260.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(title, color = c.ink, style = type.body)
            note?.let { Text(it, color = c.ink2, style = type.caption) }
        }
        control()
    }
}

/** The rows of a list or group of settings, which stand close together. */
@Composable
fun RowGroup(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(RowGap), content = content)
}

/** Left to right with the row's own gaps, the tail falling to a line of its own when the row is narrow. */
@Composable
private fun WrappingRow(
    modifier: Modifier,
    content: @Composable FlowRowScope.() -> Unit,
) {
    FlowRow(
        modifier,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        itemVerticalAlignment = Alignment.CenterVertically,
        content = content,
    )
}

/** A row's or a panel's name with its badges. */
@Composable
fun RowTitle(
    text: String,
    modifier: Modifier = Modifier,
    badges: @Composable () -> Unit = {},
) {
    FlowRow(modifier, horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp), itemVerticalAlignment = Alignment.CenterVertically) {
        Text(text, color = LocalFluent.current.ink, style = LocalFluentType.current.body.copy(fontWeight = FontWeight.SemiBold))
        badges()
    }
}

/** The line of facts under a name: a repository, a version, a path. */
@Composable
fun RowMeta(
    text: String,
    modifier: Modifier = Modifier,
) {
    Text(text, color = LocalFluent.current.ink2, style = LocalFluentType.current.mono, modifier = modifier.padding(top = 3.dp))
}

/** A package's card in a grid. [content] learns when the pointer is on it, to raise its icon. */
@Composable
fun Tile(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.(hovered: Boolean) -> Unit,
) {
    val c = LocalFluent.current
    val source = remember { MutableInteractionSource() }
    val hovered by source.collectIsHoveredAsState()
    Column(
        modifier
            .hoverable(source)
            .then(if (hovered) Modifier.dropShadow(Corners.Overlay, Shadow(radius = 20.dp, color = TILE_SHADOW, offset = DpOffset(0.dp, 8.dp))) else Modifier)
            .card(fill = if (hovered) c.cardHover else c.card)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) { content(hovered) }
}

/** An extruded icon beside a heading and a line of text. */
@Composable
fun FeatureCard(
    glyph: Glyph,
    gradient: IconGradient,
    title: String,
    text: String,
    modifier: Modifier = Modifier,
) {
    val c = LocalFluent.current
    val type = LocalFluentType.current
    val source = remember { MutableInteractionSource() }
    val hovered by source.collectIsHoveredAsState()
    val rise by animateFloatAsState(if (hovered) -2f else 0f, tween(CARD_MILLIS))
    Row(
        modifier
            .hoverable(source)
            .graphicsLayer { translationY = rise * density }
            .card(fill = if (hovered) c.cardHover else c.card)
            .padding(20.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        FeatureIcon(glyph, gradient, raised = hovered)
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(title, color = c.ink, style = type.strong)
            Text(text, color = c.ink2, style = type.small)
        }
    }
}

/** A number that sums up a page, on a raised panel. */
@Composable
fun StatCard(
    value: String,
    label: String,
    glyph: Glyph,
    gradient: IconGradient,
    modifier: Modifier = Modifier,
) {
    val c = LocalFluent.current
    val type = LocalFluentType.current
    LiftPanel(modifier.fillMaxWidth(), padding = 20.dp) { hovered ->
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            FeatureIcon(glyph, gradient, raised = hovered)
            Column {
                Text(value, color = c.ink, style = type.stat)
                Text(label, color = c.ink2, style = type.small)
            }
        }
    }
}

/** What is under way: floats over the page, or stands in it where its details are. */
@Composable
fun ActivityFlyout(
    title: String,
    modifier: Modifier = Modifier,
    status: String? = null,
    percent: Int? = null,
    action: @Composable () -> Unit = {},
) {
    val c = LocalFluent.current
    val type = LocalFluentType.current
    Row(
        modifier.flyout(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        ProgressRing(size = 22.dp)
        Column(Modifier.weight(1f)) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(title, color = c.ink, style = type.small.copy(fontWeight = FontWeight.SemiBold), maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                status?.let { Text(it, color = c.ink2, style = type.small, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false)) }
                Box(Modifier.weight(0.001f))
                percent?.let { Text("$it%", color = c.ink2, style = type.small) }
            }
            percent?.let { ProgressBar(it / 100f, Modifier.padding(top = 10.dp)) }
        }
        action()
    }
}

/** What floats over the page to be read in full: a failure, something in the way. */
@Composable
fun FlyoutNotice(
    title: String,
    text: String,
    modifier: Modifier = Modifier,
    leading: @Composable () -> Unit = {},
    action: @Composable () -> Unit = {},
) {
    val c = LocalFluent.current
    val type = LocalFluentType.current
    Row(modifier.flyout(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        leading()
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, color = c.ink, style = type.small.copy(fontWeight = FontWeight.SemiBold))
            Text(text, color = c.ink2, style = type.small)
        }
        action()
    }
}

@Composable
private fun Modifier.flyout(): Modifier {
    val c = LocalFluent.current
    val shape = Corners.Overlay
    return dropShadow(shape, Shadow(radius = 32.dp, color = FLYOUT_SHADOW, offset = DpOffset(0.dp, 16.dp)))
        .clip(shape)
        .background(c.flyout)
        .innerShadow(shape, Shadow(radius = 0.dp, color = c.rim, offset = DpOffset(0.dp, 1.dp)))
        .border(1.dp, c.strokeWindow, shape)
        .padding(start = 18.dp, top = 14.dp, end = 14.dp, bottom = 14.dp)
        .semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite }
}

/** A notice standing in a page, with what makes it worth reading in bold. */
@Composable
fun InfoBar(
    title: String,
    text: String,
    modifier: Modifier = Modifier,
) {
    val c = LocalFluent.current
    val type = LocalFluentType.current
    FlowRow(
        modifier
            .fillMaxWidth()
            .clip(Corners.Item)
            .background(c.infoSubtle)
            .border(1.dp, c.stroke, Corners.Item)
            .padding(horizontal = 18.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
        itemVerticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(20.dp).clip(CircleShape).background(c.accent), contentAlignment = Alignment.Center) { GlyphIcon(Glyph.Info, c.accentOn, 12.dp) }
        Text(title, color = c.ink, style = type.body.copy(fontWeight = FontWeight.SemiBold))
        Text(text, color = c.ink, style = type.body, modifier = Modifier.weight(1f).widthIn(min = 320.dp))
    }
}

/** Names and values in two aligned columns; the values can be selected, since they are paths and versions to copy. */
@Composable
fun FactsList(
    facts: List<Pair<String, String>>,
    modifier: Modifier = Modifier,
) {
    val c = LocalFluent.current
    val type = LocalFluentType.current
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val style = type.small
    val widest =
        remember(facts, style) {
            facts.maxOfOrNull { (name, _) -> measurer.measure(name, style).size.width } ?: 0
        }
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        for ((name, value) in facts) {
            Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                Text(name, color = c.ink2, style = style, modifier = Modifier.width(with(density) { widest.toDp() } + 1.dp))
                SelectionContainer(Modifier.weight(1f)) { Text(value, color = c.ink, style = type.code) }
            }
        }
    }
}

/** One line of a log, under a hairline unless it is the [first]. */
@Composable
fun LogRow(
    time: String,
    level: String,
    kind: BadgeKind,
    message: String,
    modifier: Modifier = Modifier,
    first: Boolean = false,
) {
    val c = LocalFluent.current
    val type = LocalFluentType.current
    Column(modifier.fillMaxWidth()) {
        if (!first) Box(Modifier.fillMaxWidth().height(1.dp).background(c.stroke))
        Row(
            Modifier.padding(vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(time, color = c.ink2, style = type.mono, modifier = Modifier.width(52.dp))
            Box(Modifier.width(70.dp), contentAlignment = Alignment.Center) { StatusBadge(level, kind) }
            SelectionContainer(Modifier.weight(1f)) { Text(message, color = c.ink, style = type.small) }
        }
    }
}

/** The featured banner: words on the left, a [scene] on the right, on the deep brand fill with a green glow. */
@Composable
fun HeroPanel(
    modifier: Modifier = Modifier,
    scene: @Composable BoxScope.() -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = Corners.Overlay
    FlowRow(
        modifier
            .fillMaxWidth()
            .dropShadow(shape, Shadow(radius = 32.dp, color = HERO_SHADOW, offset = DpOffset(0.dp, 12.dp)))
            .clip(shape)
            .background(FluentColors.Deep)
            .drawBehind { drawGlow(Offset(size.width * 0.8f, size.height / 2), size.width * 0.55f, size.height * 0.9f, HERO_GLOW) }
            .innerShadow(shape, Shadow(radius = 0.dp, color = Color.White.copy(alpha = 0.16f), offset = DpOffset(0.dp, 1.dp)))
            .border(1.dp, Color.White.copy(alpha = 0.1f), shape)
            .padding(horizontal = 48.dp, vertical = 44.dp),
        horizontalArrangement = Arrangement.spacedBy(24.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
        itemVerticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f).widthIn(min = 380.dp), verticalArrangement = Arrangement.spacedBy(18.dp), content = content)
        Box(Modifier.weight(1f).widthIn(min = 340.dp).height(320.dp), content = scene)
    }
}

/** The small label above a banner's headline. */
@Composable
fun Eyebrow(text: String) {
    Row(
        Modifier
            .heightIn(min = 28.dp)
            .clip(Corners.Control)
            .background(Color.White.copy(alpha = 0.12f))
            .border(1.dp, Color.White.copy(alpha = 0.18f), Corners.Control)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(
            Modifier
                .size(8.dp)
                .graphicsLayer { rotationZ = -18f }
                .clip(RoundedCornerShape(2.dp))
                .background(FluentColors.Coral),
        )
        Text(text, color = Color.White, style = LocalFluentType.current.caption.copy(fontWeight = FontWeight.SemiBold))
    }
}

/**
 * The banner's scene: a glass pane of [lines], a [mark] tile before it, a release [tag] floating over it and a [badge]
 * nearest of all. Compose flattens a layer, so what CSS stands apart in depth is drawn nearer by scale instead.
 */
@Composable
fun HeroScene(
    lines: List<Pair<String, String>>,
    tag: String,
    badge: String,
    modifier: Modifier = Modifier,
    mark: @Composable BoxScope.() -> Unit,
) {
    val type = LocalFluentType.current
    val float by rememberInfiniteTransition().animateFloat(0f, -8f, infiniteRepeatable(tween(FLOAT_MILLIS), RepeatMode.Reverse))
    Box(
        modifier
            .fillMaxSize()
            .graphicsLayer {
                rotationX = 18f
                rotationY = -28f
                rotationZ = 2f
                cameraDistance = SCENE_PERSPECTIVE * density
            },
    ) {
        Pane(lines)
        BrandTile(
            Brush.linearGradient(listOf(BRAND_TOP, FluentColors.Deep, BRAND_BOTTOM)),
            Modifier.align(Alignment.Center).offset(y = 24.dp).size(132.dp * NEAR),
            content = mark,
        )
        ReleaseTag(
            tag,
            Modifier.align(Alignment.TopEnd).padding(top = 44.dp, end = 72.dp).graphicsLayer { translationY = float * density },
            style = type.code.copy(fontSize = 15.sp, fontWeight = FontWeight.SemiBold),
        )
        Row(
            Modifier
                .align(Alignment.BottomStart)
                .padding(bottom = 12.dp)
                .graphicsLayer {
                    scaleX = NEAREST
                    scaleY = NEAREST
                }.heightIn(min = 38.dp)
                .dropShadow(Corners.Item, Shadow(radius = 30.dp, color = Color.Black.copy(alpha = 0.3f), offset = DpOffset(0.dp, 18.dp)))
                .clip(Corners.Item)
                .background(Color.White.copy(alpha = 0.92f))
                .padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            GlyphIcon(Glyph.Check, VERIFIED_INK, 16.dp)
            Text(badge, color = VERIFIED_INK, style = type.small.copy(fontWeight = FontWeight.SemiBold))
        }
    }
}

@Composable
private fun BoxScope.Pane(lines: List<Pair<String, String>>) {
    val type = LocalFluentType.current
    val shape = RoundedCornerShape(12.dp)
    Column(
        Modifier
            .align(Alignment.TopStart)
            .padding(start = 20.dp, top = 32.dp)
            .fillMaxWidth(0.82f)
            .fillMaxHeight(0.76f)
            .dropShadow(shape, Shadow(radius = 60.dp, color = Color.Black.copy(alpha = 0.35f), offset = DpOffset(0.dp, 40.dp)))
            .clip(shape)
            .background(Brush.linearGradient(listOf(Color.White.copy(alpha = 0.3f), Color.White.copy(alpha = 0.07f))))
            .innerShadow(shape, Shadow(radius = 0.dp, color = Color.White.copy(alpha = 0.65f), offset = DpOffset(0.dp, 1.dp)))
            .border(1.dp, Color.White.copy(alpha = 0.4f), shape),
    ) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Box(Modifier.size(28.dp, 6.dp).clip(RoundedCornerShape(3.dp)).background(Color.White.copy(alpha = 0.35f)))
            Box(Modifier.size(18.dp, 6.dp).clip(RoundedCornerShape(3.dp)).background(Color.White.copy(alpha = 0.2f)))
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(Color.White.copy(alpha = 0.16f)))
        Column(Modifier.padding(horizontal = 20.dp, vertical = 14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            lines.forEachIndexed { index, (word, rest) ->
                val alpha = if (index == 0) 1f else 0.8f
                Row(Modifier.graphicsLayer { this.alpha = alpha }, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(word, color = if (index == 0) FluentColors.Mint else PANE_INK, style = type.mono, maxLines = 1)
                    Text(rest, color = PANE_INK, style = type.mono, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

private const val CARD_MILLIS = 180
private const val FLOAT_MILLIS = 2500

// cameraDistance counts 72nds of an inch, so this is the design's CSS perspective of 1200 pixels.
private const val SCENE_PERSPECTIVE = 16.7f

// How much nearer the design's translateZ of 70 and 120 pixels brings a layer, under its perspective of 1200.
private const val NEAR = 1.06f
private const val NEAREST = 1.11f
private val TILE_SHADOW = Color(14, 40, 56, 31)
private val FLYOUT_SHADOW = Color(14, 40, 56, 56)
private val HERO_SHADOW = Color(10, 61, 54, 82)
private val HERO_GLOW = Color(61, 220, 176, 66)
private val BRAND_TOP = Color(0xFF13584F)
private val BRAND_BOTTOM = Color(0xFF072A26)
private val PANE_INK = Color(232, 244, 241, 235)
private val VERIFIED_INK = Color(0xFF12683A)
