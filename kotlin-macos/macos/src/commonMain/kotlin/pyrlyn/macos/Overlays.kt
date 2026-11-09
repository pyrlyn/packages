package pyrlyn.macos

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties

/**
 * A menu under its anchor: 4 points inside, 28-point items, the Overlay elevation.
 * Put it in the same Box as the control that opens it.
 */
@Composable
fun Menu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    offset: DpOffset = DpOffset(0.dp, MENU_GAP),
    width: Dp = MENU_WIDTH,
    focusable: Boolean = true,
    content: @Composable ColumnScope.() -> Unit,
) {
    if (!expanded) return
    val shape = RoundedCornerShape(MacRadius.lg)
    MenuPopup(offset, onDismissRequest, focusable) {
        Column(
            modifier
                // The shadows reach 72 points down; the popup's own bounds would clip them.
                .padding(SHADOW_ROOM)
                .widthIn(min = width)
                .elevatedSurface(shape)
                .padding(MacSpace.sm),
            content = content,
        )
    }
}

@Composable
private fun MenuPopup(
    offset: DpOffset,
    onDismissRequest: () -> Unit,
    focusable: Boolean,
    content: @Composable () -> Unit,
) {
    val density = LocalDensity.current
    val provider =
        remember(offset, density) {
            with(density) { BelowAnchor(offset.x.roundToPx(), offset.y.roundToPx(), SHADOW_ROOM.roundToPx()) }
        }
    Popup(provider, onDismissRequest, PopupProperties(focusable = focusable), content)
}

/** Puts the menu itself, not its shadow room, [dx] and [dy] under the anchor, kept inside the window. */
private class BelowAnchor(
    private val dx: Int,
    private val dy: Int,
    private val room: Int,
) : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize,
    ): IntOffset {
        val below = anchorBounds.bottom + dy - room
        val above = anchorBounds.top - dy - popupContentSize.height + room
        val y = if (below + popupContentSize.height - room > windowSize.height && above + room >= 0) above else below
        val maxX = (windowSize.width - popupContentSize.width + room).coerceAtLeast(-room)
        val x = (anchorBounds.left + dx - room).coerceIn(-room, maxX)
        return IntOffset(x, y)
    }
}

/** One menu entry; [checked] marks the current choice, [danger] a destructive one. */
@Composable
fun MenuItem(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    checked: Boolean = false,
    danger: Boolean = false,
    enabled: Boolean = true,
    icon: MacIcon? = null,
    shortcut: String? = null,
) {
    MenuItem(onClick, modifier, checked, danger, enabled) {
        icon?.let { Icon(it, tint = if (danger) LocalMacContentColor.current else Mac.colors.textSecondary) }
        Label(text, Modifier.weight(1f), maxLines = 1)
        shortcut?.let { Label(it, color = Mac.colors.textTertiary, style = Mac.type.caption) }
    }
}

/** A menu entry around any content, which reads its colour and style from [LocalMacContentColor] and [LocalMacTextStyle]. */
@Composable
fun MenuItem(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    checked: Boolean = false,
    danger: Boolean = false,
    enabled: Boolean = true,
    content: @Composable RowScope.() -> Unit,
) {
    val c = Mac.colors
    val interaction = remember { MutableInteractionSource() }
    val state = interaction.controlState()
    val ink = if (danger) c.statusError else c.textPrimary
    val shape = RoundedCornerShape(MacRadius.md)
    Row(
        modifier
            .fillMaxWidth()
            .height(MENU_ITEM)
            .clip(shape)
            .background(rowFill(selected = false, hovered = enabled && (state.hovered || state.focused)))
            .clickable(interaction, null, enabled, role = Role.Button, onClick = onClick)
            .alpha(if (enabled) 1f else DISABLED_ALPHA)
            .padding(horizontal = MacSpace.lg),
        horizontalArrangement = Arrangement.spacedBy(MacSpace.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ProvideMacContent(ink, Mac.type.body) { content() }
        if (checked) Icon(MacIcon.Check, tint = c.accentInk, size = CHECK)
    }
}

/** A group's name over its items, such as "Local" and "Cloud". */
@Composable
fun MenuSection(
    label: String,
    modifier: Modifier = Modifier,
) {
    Label(
        label,
        modifier.padding(start = MacSpace.lg, end = MacSpace.lg, top = MacSpace.sm, bottom = MacSpace.xxs),
        Mac.colors.textTertiary,
        Mac.type.caption,
        maxLines = 1,
    )
}

/** A hairline between groups of menu items. */
@Composable
fun MenuSeparator(modifier: Modifier = Modifier) {
    Rule(modifier.padding(horizontal = MacSpace.lg, vertical = MacSpace.xs))
}

private val MENU_ITEM = 30.dp
private val MENU_WIDTH = 232.dp
private val MENU_GAP = 4.dp
private val CHECK = 14.dp
private val SHADOW_ROOM = 48.dp

/**
 * An icon that shows [content] in a menu-styled card while the pointer rests on it, for detail that needs no click.
 * The card takes no focus, so typing carries on in the field next to it.
 */
@Composable
fun HoverCard(
    icon: MacIcon,
    description: String,
    modifier: Modifier = Modifier,
    tint: Color = Mac.colors.textSecondary,
    width: Dp = MENU_WIDTH,
    content: @Composable ColumnScope.() -> Unit,
) {
    val onIcon = remember { MutableInteractionSource() }
    val onCard = remember { MutableInteractionSource() }
    val iconHovered by onIcon.collectIsHoveredAsState()
    val cardHovered by onCard.collectIsHoveredAsState()
    // The card's shadow room covers the icon and takes the pointer from it, so resting on the card keeps it open.
    val open = iconHovered || cardHovered
    Box(
        modifier
            .size(MacSize.iconButton)
            .semantics { contentDescription = description }
            .hoverable(onIcon),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, tint = if (open) Mac.colors.textPrimary else tint)
        Menu(open, {}, Modifier.hoverable(onCard), width = width, focusable = false, content = content)
    }
}

/**
 * A button in two parts on a raised card: the main part runs [onClick], the chevron opens [menu] below it, as macOS's
 * "Open in" pickers do. [menu] gets a function that closes it, for items that act and leave.
 */
@Composable
fun SplitButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    menuDescription: String = "More",
    menuWidth: Dp = MENU_WIDTH,
    menu: @Composable ColumnScope.(close: () -> Unit) -> Unit,
    content: @Composable RowScope.() -> Unit,
) {
    val c = Mac.colors
    var open by remember { mutableStateOf(false) }
    val main = remember { MutableInteractionSource() }
    val more = remember { MutableInteractionSource() }
    Box(modifier) {
        Row(
            Modifier
                .height(MacSize.picker)
                .raisedSurface(RoundedCornerShape(MacRadius.md))
                .alpha(if (enabled) 1f else DISABLED_ALPHA),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                Modifier
                    .fillMaxHeight()
                    .control(RectangleShape, Color.Transparent, null, main, enabled, Role.Button, onClick)
                    .padding(horizontal = MacSpace.lg),
                horizontalArrangement = Arrangement.spacedBy(MacSpace.sm),
                verticalAlignment = Alignment.CenterVertically,
            ) { ProvideMacContent(c.textPrimary, Mac.type.smallEmphasized) { content() } }
            VerticalRule()
            Box(
                Modifier
                    .fillMaxHeight()
                    .semantics { contentDescription = menuDescription }
                    .control(RectangleShape, c.bgFill, null, more, enabled, Role.DropdownList) { open = true }
                    .padding(horizontal = MacSpace.md),
                contentAlignment = Alignment.Center,
            ) { Icon(MacIcon.ChevronDown, tint = c.textSecondary, size = CHEVRON) }
        }
        Menu(open, { open = false }, width = menuWidth) { menu { open = false } }
    }
}
