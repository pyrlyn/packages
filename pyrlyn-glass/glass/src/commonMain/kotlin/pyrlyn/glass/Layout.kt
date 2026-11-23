// The window's frame: sidebar rows, the toolbar and the grid that fits its columns to the room.
package pyrlyn.glass

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp

/** One row of a sidebar. [description] replaces what a screen reader says when the row shows more than its label. */
@Composable
fun SidebarItem(
    label: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    description: String? = null,
    leading: @Composable () -> Unit,
    trailing: @Composable () -> Unit = {},
    onClick: () -> Unit,
) {
    val glass = LocalGlass.current
    val source = remember { MutableInteractionSource() }
    val hovered by source.collectIsHoveredAsState()
    val shape = RoundedCornerShape(10.dp)
    var item = modifier.fillMaxWidth().heightIn(min = SIDE_ITEM)
    if (selected) {
        for (lift in glass.activeLift) item = item.dropShadow(shape, Shadow(radius = lift.blur, color = lift.color, offset = lift.offset))
    }
    Row(
        item
            .clip(shape)
            .background(if (selected) Brush.verticalGradient(glass.active) else SolidColor(if (hovered) glass.hover else Color.Transparent))
            .hoverable(source)
            .selectable(selected, source, indication = null, role = Role.Tab, onClick = onClick)
            .padding(horizontal = 10.dp)
            .semantics { description?.let { contentDescription = it } },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        leading()
        Text(label, color = glass.ink, style = body(SIDE_LABEL, if (selected) FontWeight.SemiBold else FontWeight.Normal), modifier = Modifier.weight(1f))
        trailing()
    }
}

/** A sidebar group's small heading. */
@Composable
fun SidebarLabel(label: String) {
    Text(
        label.uppercase(),
        color = LocalGlass.current.ink2,
        style = body(TextSize.CAPTION, FontWeight.SemiBold),
        modifier = Modifier.padding(start = 10.dp, end = 10.dp, top = 18.dp, bottom = 6.dp),
    )
}

/** A count that asks for attention. */
@Composable
fun CountBadge(count: Int) {
    val glass = LocalGlass.current
    val shape = RoundedCornerShape(10.dp)
    Box(
        Modifier
            .heightIn(min = COUNT_HEIGHT)
            .widthIn(min = COUNT_HEIGHT)
            .dropShadow(shape, Shadow(radius = 6.dp, color = glass.attentionFill.copy(alpha = COUNT_GLOW), offset = DpOffset(0.dp, 2.dp)))
            .clip(shape)
            .background(glass.attentionFill)
            .padding(horizontal = 6.dp),
        contentAlignment = Alignment.Center,
    ) { Text("$count", color = glass.attentionOn, style = body(COUNT, FontWeight.Bold)) }
}

/** A page's title row: [leading], then [crumb] and [title], then [trailing] at the end. */
@Composable
fun Toolbar(
    title: String,
    modifier: Modifier = Modifier,
    crumb: String? = null,
    leading: @Composable () -> Unit = {},
    trailing: @Composable RowScope.() -> Unit = {},
) {
    val glass = LocalGlass.current
    Row(modifier.fillMaxWidth().heightIn(min = TOOLBAR), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        leading()
        crumb?.let { Text("$it ›", color = glass.ink2, style = body(SIDE_LABEL)) }
        Text(title, color = glass.ink, style = display(TITLE), maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.semantics { heading() })
        Spacer(Modifier.weight(1f))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp), content = trailing)
    }
}

/** The capsule of back and forward. */
@Composable
fun NavigationArrows(
    canGoBack: Boolean,
    canGoForward: Boolean,
    backLabel: String,
    forwardLabel: String,
    onBack: () -> Unit,
    onForward: () -> Unit,
) {
    val glass = LocalGlass.current
    val shape = RoundedCornerShape(percent = 50)
    Row(
        Modifier
            .clip(shape)
            .background(glass.pill)
            .border(1.dp, glass.pillRim, shape)
            .padding(3.dp),
    ) {
        GlassIconButton(Glyph.Back, backLabel, enabled = canGoBack, onClick = onBack)
        GlassIconButton(Glyph.Forward, forwardLabel, enabled = canGoForward, onClick = onForward)
    }
}

/** How many columns of at least [min] fit [width], for [count] cells. */
fun autoColumns(
    width: Dp,
    min: Dp,
    gap: Dp,
    count: Int,
) = ((width + gap) / (min + gap)).toInt().coerceIn(1, count.coerceAtLeast(1))

/** As many equal cells of at least [min] as fit, CSS's `auto-fit` grid; a row's cells share its height. */
@Composable
fun AutoGrid(
    count: Int,
    min: Dp,
    gap: Dp,
    cell: @Composable (Int) -> Unit,
) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val columns = autoColumns(maxWidth, min, gap, count)
        Column(verticalArrangement = Arrangement.spacedBy(gap)) {
            for (row in (0 until count).chunked(columns)) {
                Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(gap)) {
                    for (index in row) Box(Modifier.weight(1f).fillMaxHeight()) { cell(index) }
                    repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
}

private const val SIDE_LABEL = 14f
private const val COUNT = 12f
private const val COUNT_GLOW = 0.35f
private const val TITLE = 22
private val SIDE_ITEM = 36.dp
private val COUNT_HEIGHT = 20.dp
private val TOOLBAR = 52.dp
