// The sizes are the design system's own values; a name for each would only repeat it.
@file:Suppress("MagicNumber")

package pyrlyn.adwaita

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import pyrlyn.glass.Glyph
import pyrlyn.glass.GlyphIcon

/** The window's top: three parts, the middle one centred on the window whatever the sides hold. */
@Composable
fun HeaderBar(
    modifier: Modifier = Modifier,
    start: @Composable RowScope.() -> Unit = {},
    center: @Composable () -> Unit = {},
    end: @Composable RowScope.() -> Unit = {},
) {
    val c = LocalAdwaita.current
    Column(modifier.fillMaxWidth().background(HEADER_FILL)) {
        Row(Modifier.fillMaxWidth().height(51.dp).padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp), content = start)
            center()
            Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp, Alignment.End), content = end)
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(c.line))
    }
}

/** Minimize, maximize and close, for a window that draws its own frame. The words are the design system's own. */
@Composable
fun WindowControls(
    maximized: Boolean,
    modifier: Modifier = Modifier,
    onMinimize: () -> Unit,
    onToggleMaximize: () -> Unit,
    onClose: () -> Unit,
) {
    Row(modifier.padding(start = 10.dp, end = 8.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        WindowButton("Minimize", onMinimize) { ink, line ->
            val y = size.height * 0.72f
            drawLine(ink, Offset(0f, y), Offset(size.width, y), line)
        }
        WindowButton(if (maximized) "Restore" else "Maximize", onToggleMaximize) { ink, line ->
            val inset = if (maximized) size.width * 0.16f else 0f
            drawRect(ink, Offset(inset, inset), size.copy(size.width - inset * 2, size.height - inset * 2), style = Stroke(line))
        }
        WindowButton("Close", onClose) { ink, line ->
            drawLine(ink, Offset.Zero, Offset(size.width, size.height), line)
            drawLine(ink, Offset(size.width, 0f), Offset(0f, size.height), line)
        }
    }
}

@Composable
private fun WindowButton(
    label: String,
    onClick: () -> Unit,
    draw: DrawScope.(Color, Float) -> Unit,
) {
    val c = LocalAdwaita.current
    val source = remember { MutableInteractionSource() }
    val hovered by source.collectIsHoveredAsState()
    Box(
        Modifier
            .size(24.dp)
            .clip(CircleShape)
            .background(if (hovered) c.fill3 else c.fill2)
            .focusRing(source, CircleShape)
            .hoverable(source)
            .clickable(source, null, role = Role.Button, onClick = onClick)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(9.dp)) { draw(c.ink, 1.5.dp.toPx()) }
    }
}

/** A page: it scrolls, and its column never grows past the design's 960. */
@Composable
fun Clamp(
    modifier: Modifier = Modifier,
    scroll: ScrollState = rememberScrollState(),
    content: @Composable ColumnScope.() -> Unit,
) {
    Box(
        modifier
            .fillMaxSize()
            .verticalScroll(scroll)
            .padding(start = 24.dp, end = 24.dp, top = 28.dp, bottom = 100.dp),
        contentAlignment = Alignment.TopCenter,
    ) {
        Column(Modifier.widthIn(max = 960.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(28.dp), content = content)
    }
}

/** A titled group: [title] with its [description] and [suffix], over its [content]. */
@Composable
fun PreferencesGroup(
    title: String,
    modifier: Modifier = Modifier,
    description: String? = null,
    suffix: @Composable RowScope.() -> Unit = {},
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(Modifier.weight(1f)) {
                Heading(title)
                description?.let { Text(it, color = LocalAdwaita.current.ink2, style = LocalAdwaitaType.current.caption, modifier = Modifier.padding(top = 2.dp)) }
            }
            suffix()
        }
        content()
    }
}

/** A rounded list of rows, a line between each. */
@Composable
fun BoxedList(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(modifier.fillMaxWidth().boxed()) { content() }
}

/** The line between two rows of a [BoxedList]. */
@Composable
fun RowLine() {
    Box(Modifier.fillMaxWidth().height(1.dp).background(LocalAdwaita.current.line))
}

/** A row of a [BoxedList]: [leading], its text, and [trailing] at the end. With [onClick] the whole row presses. */
@Composable
fun ActionRow(
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    onClick: (() -> Unit)? = null,
    leading: @Composable RowScope.() -> Unit = {},
    trailing: @Composable RowScope.() -> Unit = {},
    content: @Composable ColumnScope.() -> Unit,
) {
    val c = LocalAdwaita.current
    val source = remember { MutableInteractionSource() }
    val hovered by source.collectIsHoveredAsState()
    val fill =
        when {
            onClick != null && hovered -> ROW_HOVER
            selected -> c.accent.copy(alpha = 0.08f)
            else -> Color.Transparent
        }
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = 60.dp)
            .background(fill)
            .then(
                if (onClick == null) {
                    Modifier
                } else {
                    Modifier.focusRing(source, Corners.Medium).hoverable(source).clickable(source, null, role = Role.Button, onClick = onClick)
                },
            ).padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        leading()
        Column(Modifier.weight(1f), content = content)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp), content = trailing)
    }
}

@Composable
fun RowTitle(
    text: String,
    modifier: Modifier = Modifier,
) {
    Text(text, color = LocalAdwaita.current.ink, style = LocalAdwaitaType.current.bodyStrong, modifier = modifier)
}

@Composable
fun RowText(
    text: String,
    modifier: Modifier = Modifier,
) {
    Text(text, color = LocalAdwaita.current.ink2, style = LocalAdwaitaType.current.caption, modifier = modifier.padding(top = 2.dp))
}

@Composable
fun RowMono(
    text: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text,
        color = LocalAdwaita.current.ink2,
        style = LocalAdwaitaType.current.monoSmall,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier.padding(top = 2.dp),
    )
}

/** A card of the grid on a browse page: an icon, a name and a line, and what can be done at the end. */
@Composable
fun Tile(
    name: String,
    description: String,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    leading: @Composable RowScope.() -> Unit = {},
    trailing: @Composable RowScope.() -> Unit = {},
) {
    val c = LocalAdwaita.current
    val type = LocalAdwaitaType.current
    val source = remember { MutableInteractionSource() }
    val hovered by source.collectIsHoveredAsState()
    Row(
        modifier
            .clip(Corners.Medium)
            .background(if (hovered) TILE_HOVER else c.fill1)
            .border(1.dp, c.line, Corners.Medium)
            .then(
                if (onClick == null) {
                    Modifier
                } else {
                    Modifier.focusRing(source, Corners.Medium).hoverable(source).clickable(source, null, role = Role.Button, onClick = onClick)
                },
            ).padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        leading()
        Column(Modifier.weight(1f)) {
            Text(name, color = c.ink, style = type.bodyStrong, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(description, color = c.ink2, style = type.caption, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 2.dp))
        }
        trailing()
    }
}

/** Where a page sits: a link back, then its own name. */
@Composable
fun Breadcrumb(
    parent: String,
    current: String,
    modifier: Modifier = Modifier,
    onParent: () -> Unit,
) {
    val c = LocalAdwaita.current
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        TextLink(parent, onClick = onParent)
        GlyphIcon(Glyph.Forward, c.ink2, 12.dp)
        Text(current, color = c.ink2, style = LocalAdwaitaType.current.caption)
    }
}

private val HEADER_FILL = Color(255, 255, 255, 10)
private val ROW_HOVER = Color(255, 255, 255, 13)
private val TILE_HOVER = Color(255, 255, 255, 28)

/** A page's title, with what acts on the whole page at its end. */
@Composable
fun PageHead(
    title: String,
    modifier: Modifier = Modifier,
    actions: @Composable RowScope.() -> Unit = {},
) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            title,
            color = LocalAdwaita.current.ink,
            style = LocalAdwaitaType.current.page,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f).semantics { heading() },
        )
        actions()
    }
}
