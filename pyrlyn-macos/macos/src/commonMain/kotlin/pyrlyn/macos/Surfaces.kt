package pyrlyn.macos

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.draw.innerShadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp

/** The Overlay elevation: a near and a far drop shadow and a lit top edge, for sheets, menus and the window. */
@Composable
fun Modifier.overlayElevation(shape: Shape): Modifier {
    val c = Mac.colors
    return this
        .dropShadow(shape, Shadow(radius = 48.dp, color = c.shadowFar, spread = (-8).dp, offset = DpOffset(0.dp, 24.dp)))
        .dropShadow(shape, Shadow(radius = 8.dp, color = c.shadowNear, offset = DpOffset(0.dp, 4.dp)))
        .innerShadow(shape, Shadow(radius = 0.dp, color = c.highlight, offset = DpOffset(0.dp, 1.dp)))
}

/** A floating surface on `bg/elevated` with the subtle border and the Overlay elevation. */
@Composable
fun Modifier.elevatedSurface(shape: Shape): Modifier {
    val c = Mac.colors
    return overlayElevation(shape)
        .clip(shape)
        .background(c.bgElevated)
        .border(MacSize.hairline, c.borderSubtle, shape)
}

/** A card raised off the page with one soft shadow and a hairline, for the composer. */
@Composable
fun Modifier.raisedSurface(shape: Shape): Modifier {
    val c = Mac.colors
    return this
        .dropShadow(shape, Shadow(radius = 24.dp, color = c.shadowNear, spread = (-4).dp, offset = DpOffset(0.dp, 6.dp)))
        .clip(shape)
        .background(c.bgElevated)
        .border(MacSize.hairline, c.borderHairline, shape)
}

/** The dimming layer under a sheet; a click on it dismisses. */
@Composable
fun Scrim(
    onDismiss: (() -> Unit)?,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit = {},
) {
    val interaction = remember { MutableInteractionSource() }
    Box(
        modifier
            .fillMaxSize()
            .background(Mac.colors.scrim)
            .then(if (onDismiss != null) Modifier.clickable(interaction, null, onClick = onDismiss) else Modifier),
        content = content,
    )
}

/** A modal sheet: 560 points wide, 28 inside, its parts 16 apart. Swallows clicks so the scrim behind does not see them. */
@Composable
fun Sheet(
    modifier: Modifier = Modifier,
    width: Dp = MacSize.sheet,
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = RoundedCornerShape(MacRadius.xl)
    val interaction = remember { MutableInteractionSource() }
    Column(
        modifier
            .width(width)
            .elevatedSurface(shape)
            .clickable(interaction, null, onClick = {})
            .padding(MacSpace.xxxxl),
        verticalArrangement = Arrangement.spacedBy(MacSpace.xxxl),
        content = content,
    )
}

/** A sheet's title and the sentence under it. */
@Composable
fun SheetHeading(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(MacSpace.xs)) {
        Label(title, style = Mac.type.title, color = Mac.colors.textPrimary)
        subtitle?.let { Label(it, style = Mac.type.small, color = Mac.colors.textSecondary) }
    }
}

/** A sheet's closing row: the buttons at the trailing edge, 8 apart. */
@Composable
fun SheetButtons(
    modifier: Modifier = Modifier,
    leading: @Composable () -> Unit = {},
    content: @Composable () -> Unit,
) {
    Row(
        modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(MacSpace.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        leading()
        Box(Modifier.weight(1f))
        content()
    }
}

/** A one-point `border/hairline` rule across the width. */
@Composable
fun Rule(
    modifier: Modifier = Modifier,
    color: Color = Mac.colors.borderHairline,
) {
    Box(modifier.fillMaxWidth().height(MacSize.hairline).background(color))
}

/** A one-point rule down the height, between panes. */
@Composable
fun VerticalRule(
    modifier: Modifier = Modifier,
    color: Color = Mac.colors.borderHairline,
) {
    Box(modifier.fillMaxHeight().width(MacSize.hairline).background(color))
}

/** The rounded square that leads a list row: recent folders, plugins, capabilities, the app's own mark. */
@Composable
fun IconTile(
    icon: MacIcon,
    modifier: Modifier = Modifier,
    tint: Color = Mac.colors.accentInk,
    size: Dp = MacSize.iconTile,
) {
    Box(
        modifier.size(size).background(Mac.colors.bgFill, RoundedCornerShape(MacRadius.md)),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, tint = tint) }
}

/** A status is a dot and a word, never the colour alone: this is the dot. */
@Composable
fun StatusDot(
    tone: Tone,
    modifier: Modifier = Modifier,
    size: Dp = MacSize.statusDot,
) {
    Box(modifier.size(size).background(tone.ink(), CircleShape))
}

/** The traffic lights, for a window that draws its own title bar. */
@Composable
fun WindowControls(
    onClose: () -> Unit,
    onMinimize: () -> Unit,
    onZoom: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = Mac.colors
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(TRAFFIC_GAP)) {
        Light(c.windowClose, "Close", onClose)
        Light(c.windowMinimize, "Minimize", onMinimize)
        Light(c.windowZoom, "Zoom", onZoom)
    }
}

@Composable
private fun Light(
    color: Color,
    name: String,
    onClick: () -> Unit,
) {
    Box(
        Modifier
            .size(TRAFFIC_LIGHT)
            .clip(CircleShape)
            .background(color)
            .semantics { contentDescription = name }
            .clickable(role = Role.Button, onClick = onClick),
    )
}

private val TRAFFIC_LIGHT = 12.dp
private val TRAFFIC_GAP = 8.dp
