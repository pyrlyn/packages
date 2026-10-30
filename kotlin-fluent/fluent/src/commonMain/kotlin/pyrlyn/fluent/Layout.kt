// The sizes and alphas are the design system's own values; a name for each would only repeat it.
@file:Suppress("MagicNumber")

// The frame of a window: the title bar, the navigation view, the content layer and a page's head.

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

/** The window's title bar: [leading] at its start, [center] in the middle of the rest, and [trailing] against its end. */
@Composable
fun TitleBar(
    modifier: Modifier = Modifier,
    leading: @Composable RowScope.() -> Unit = {},
    center: @Composable BoxScope.() -> Unit = {},
    trailing: @Composable RowScope.() -> Unit = {},
) {
    Row(
        modifier.fillMaxWidth().height(48.dp).padding(start = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        leading()
        Box(Modifier.weight(1f), contentAlignment = Alignment.Center, content = center)
        Row(Modifier.fillMaxHeight(), content = trailing)
    }
}

/** Minimize, maximize and close, for a window that draws its own frame. */
@Composable
fun CaptionButtons(
    maximized: Boolean,
    onMinimize: () -> Unit,
    onToggleMaximize: () -> Unit,
    onClose: () -> Unit,
) {
    CaptionButton("Minimize", onClick = onMinimize) { ink ->
        drawLine(ink, Offset(0f, size.height / 2), Offset(size.width, size.height / 2), 1.dp.toPx())
    }
    CaptionButton(if (maximized) "Restore" else "Maximize", onClick = onToggleMaximize) { ink ->
        val line = Stroke(1.dp.toPx())
        if (maximized) {
            val shift = 2.dp.toPx()
            val front = Size(size.width - shift, size.height - shift)
            drawRect(ink, Offset(0f, shift), front, style = line)
            val back = Path()
            back.moveTo(shift, shift)
            back.lineTo(shift, 0f)
            back.lineTo(size.width, 0f)
            back.lineTo(size.width, size.height - shift)
            back.lineTo(size.width - shift, size.height - shift)
            drawPath(back, ink, style = line)
        } else {
            drawRoundRect(ink, style = line, cornerRadius = CornerRadius(1.5.dp.toPx()))
        }
    }
    CaptionButton("Close", close = true, onClick = onClose) { ink ->
        drawLine(ink, Offset.Zero, Offset(size.width, size.height), 1.dp.toPx())
        drawLine(ink, Offset(size.width, 0f), Offset(0f, size.height), 1.dp.toPx())
    }
}

@Composable
private fun CaptionButton(
    label: String,
    close: Boolean = false,
    onClick: () -> Unit,
    draw: DrawScope.(Color) -> Unit,
) {
    val c = LocalFluent.current
    val source = remember { MutableInteractionSource() }
    val hovered by source.collectIsHoveredAsState()
    val fill =
        when {
            hovered && close -> FluentColors.DangerFill
            hovered -> c.hover
            else -> Color.Transparent
        }
    val ink = if (hovered && close) Color.White else c.ink
    Box(
        Modifier
            .width(46.dp)
            .fillMaxHeight()
            .background(fill)
            .hoverable(source)
            .clickable(source, null, role = Role.Button, onClick = onClick)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) { Canvas(Modifier.size(10.dp)) { draw(ink) } }
}

/** A section of the navigation view. [leading] is its icon or dot; [badge] is a count beside it. */
@Composable
fun NavItem(
    text: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    badge: Int? = null,
    badgeUpdate: Boolean = false,
    leading: @Composable () -> Unit,
    onClick: () -> Unit,
) {
    val c = LocalFluent.current
    val type = LocalFluentType.current
    val source = remember { MutableInteractionSource() }
    val hovered by source.collectIsHoveredAsState()
    val fill =
        when {
            selected -> c.selected
            hovered -> c.hover
            else -> Color.Transparent
        }
    Box(
        modifier
            .fillMaxWidth()
            .heightIn(min = 38.dp)
            .clip(Corners.Control)
            .background(fill)
            .focusRing(source, Corners.Control)
            .hoverable(source)
            .selectable(selected, source, null, role = Role.Tab, onClick = onClick),
        contentAlignment = Alignment.CenterStart,
    ) {
        if (selected) Box(Modifier.size(3.dp, 16.dp).clip(RoundedCornerShape(2.dp)).background(c.accent))
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            leading()
            Text(
                text,
                color = c.ink,
                style = type.body.copy(fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal),
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            badge?.let { NavBadge(it, badgeUpdate) }
        }
    }
}

/** A navigation icon, in accent ink when its section is chosen. */
@Composable
fun NavIcon(
    glyph: Glyph,
    selected: Boolean,
) {
    val c = LocalFluent.current
    GlyphIcon(glyph, if (selected) c.accentInk else c.ink, 18.dp)
}

/** The small square that stands for a source. */
@Composable
fun NavDot(color: Color) {
    Box(
        Modifier
            .padding(horizontal = 3.dp)
            .size(10.dp)
            .clip(RoundedCornerShape(3.dp))
            .background(color),
    )
}

@Composable
private fun NavBadge(
    count: Int,
    update: Boolean,
) {
    val c = LocalFluent.current
    Box(
        Modifier
            .heightIn(min = 18.dp)
            .widthIn(min = 18.dp)
            .clip(RoundedCornerShape(9.dp))
            .background(if (update) c.update else c.accent)
            .padding(horizontal = 5.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(count.toString(), color = if (update) c.updateOn else c.accentOn, style = LocalFluentType.current.badge.copy(fontSize = 11.sp, fontWeight = FontWeight.Bold))
    }
}

/** The heading of a group of navigation items. */
@Composable
fun NavLabel(text: String) {
    Text(
        text,
        color = LocalFluent.current.ink2,
        style = LocalFluentType.current.caption.copy(fontWeight = FontWeight.SemiBold),
        modifier = Modifier.padding(start = 12.dp, top = 18.dp, end = 12.dp, bottom = 6.dp).semantics { heading() },
    )
}

/** The layer pages stand on: it meets the title bar and the navigation with a lit edge, and the window with none. */
@Composable
fun ContentLayer(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    val c = LocalFluent.current
    Box(
        modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(topStart = 8.dp))
            .background(c.layer)
            .drawBehind {
                val inset = 0.5.dp.toPx()
                val corner = 8.dp.toPx()
                val edge = Path()
                edge.moveTo(inset, size.height)
                edge.lineTo(inset, corner)
                edge.quadraticTo(inset, inset, corner, inset)
                edge.lineTo(size.width, inset)
                drawPath(edge, c.stroke, style = Stroke(1.dp.toPx()))
            },
        content = content,
    )
}

/** A page's title with what acts on the page across from it, on a line of their own when there is no room. */
@Composable
fun PageHead(
    modifier: Modifier = Modifier,
    title: @Composable () -> Unit,
    actions: @Composable RowScope.() -> Unit = {},
) {
    FlowRow(
        modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalArrangement = Arrangement.spacedBy(12.dp),
        itemVerticalAlignment = Alignment.CenterVertically,
    ) {
        title()
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp), content = actions)
    }
}

@Composable
fun PageTitle(
    text: String,
    modifier: Modifier = Modifier,
) {
    Text(text, color = LocalFluent.current.ink, style = LocalFluentType.current.title, modifier = modifier.semantics { heading() })
}

/** The way back from a detail page, at the size of a page title. */
@Composable
fun Breadcrumb(
    parent: String,
    current: String,
    modifier: Modifier = Modifier,
    onParent: () -> Unit,
) {
    val c = LocalFluent.current
    val type = LocalFluentType.current
    val source = remember { MutableInteractionSource() }
    val hovered by source.collectIsHoveredAsState()
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            parent,
            color = if (hovered) c.ink else c.ink2,
            style = type.title,
            maxLines = 1,
            modifier =
                Modifier
                    .focusRing(source, Corners.Control)
                    .hoverable(source)
                    .clickable(source, null, role = Role.Button, onClick = onParent),
        )
        GlyphIcon(Glyph.Forward, c.ink2, 16.dp)
        Text(current, color = c.ink, style = type.title, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.semantics { heading() })
    }
}
