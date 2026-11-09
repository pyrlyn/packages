package pyrlyn.macos

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** The upper-case group header of a navigator or list. */
@Composable
fun SectionLabel(
    label: String,
    modifier: Modifier = Modifier,
) {
    Box(modifier.padding(start = MacSpace.md, end = MacSpace.md, top = MacSpace.lg, bottom = MacSpace.xs)) {
        Label(label.uppercase(), color = Mac.colors.textSecondary, style = Mac.type.sectionLabel, maxLines = 1)
    }
}

/**
 * A sidebar item: a status dot, a title and the line under it. The dot's [tone] says the state; [subtitle] says it in words.
 * [meta] is a short note at the end of the title line, such as how long ago the item last changed.
 * With an [icon], the item leads with it and the dot becomes a badge on its bottom-right corner.
 */
@Composable
fun SidebarRow(
    title: String,
    subtitle: String,
    tone: Tone,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    meta: String? = null,
    icon: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    SelectableRow(selected, onClick, modifier, RoundedCornerShape(MacRadius.sm), MacSpace.sm) {
        if (icon == null) StatusDot(tone) else BadgedIcon(icon, tone)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(MacSpace.xxs)) {
            Row(horizontalArrangement = Arrangement.spacedBy(MacSpace.sm)) {
                Label(
                    title,
                    Modifier.weight(1f).alignByBaseline(),
                    Mac.colors.textPrimary,
                    if (selected) Mac.type.bodyEmphasized else Mac.type.body,
                    maxLines = 1,
                )
                meta?.let { Label(it, Modifier.alignByBaseline(), Mac.colors.textSecondary, Mac.type.caption, maxLines = 1) }
            }
            Label(subtitle, color = Mac.colors.textSecondary, style = Mac.type.caption, maxLines = 1)
        }
        trailing?.invoke()
    }
}

/**
 * The badge hangs past the icon's corner, ringed in the sidebar's colour, so it reads as a mark
 * on the icon rather than part of its art.
 */
@Composable
private fun BadgedIcon(
    icon: @Composable () -> Unit,
    tone: Tone,
) {
    Box {
        icon()
        Box(
            Modifier
                .align(Alignment.BottomEnd)
                .offset(BADGE_OVERHANG, BADGE_OVERHANG)
                .background(Mac.colors.bgChrome, CircleShape)
                .padding(BADGE_RING),
        ) { StatusDot(tone) }
    }
}

/** A row of the Settings sidebar, also used for the main sidebar's footer links. */
@Composable
fun SettingsItem(
    icon: MacIcon,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    trailing: (@Composable () -> Unit)? = null,
) {
    SelectableRow(
        selected,
        onClick,
        modifier.height(MacSize.settingsItem),
        RoundedCornerShape(MacRadius.md),
        0.dp,
        MacSpace.lg,
    ) {
        Icon(icon, tint = if (selected) Mac.colors.textPrimary else Mac.colors.textSecondary)
        Label(label, Modifier.weight(1f), Mac.colors.textPrimary, Mac.type.body, maxLines = 1)
        trailing?.invoke()
    }
}

/** A dot and a word for a status bar: a process, a remote host, the agent's state. */
@Composable
fun Status(
    label: String,
    tone: Tone,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier.height(MacSize.icon),
        horizontalArrangement = Arrangement.spacedBy(MacSpace.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        StatusDot(tone, size = STATUS_DOT)
        Label(label, color = Mac.colors.textSecondary, style = Mac.type.caption, maxLines = 1)
    }
}

/** The 44-point bar along the top of a pane, on `bg/chrome` with a hairline under it. */
@Composable
fun Toolbar(
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit,
) {
    val c = Mac.colors
    Row(
        modifier
            .fillMaxWidth()
            .height(MacSize.toolbar)
            .background(c.bgChrome)
            .bottomHairline(c.borderHairline)
            .padding(horizontal = MacSpace.xxxl),
        horizontalArrangement = Arrangement.spacedBy(MacSpace.xl),
        verticalAlignment = Alignment.CenterVertically,
        content = content,
    )
}

/** A list row: an optional leading slot, a title and detail, and a trailing slot. */
@Composable
fun ListRow(
    title: String,
    modifier: Modifier = Modifier,
    detail: String? = null,
    selected: Boolean = false,
    onClick: (() -> Unit)? = null,
    leading: (@Composable () -> Unit)? = null,
    trailing: (@Composable RowScope.() -> Unit)? = null,
) {
    val body: @Composable RowScope.() -> Unit = {
        leading?.invoke()
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(MacSpace.xxs)) {
            Label(title, color = Mac.colors.textPrimary, style = Mac.type.bodyEmphasized, maxLines = 1)
            detail?.let { Label(it, color = Mac.colors.textSecondary, style = Mac.type.small, maxLines = 2) }
        }
        trailing?.invoke(this)
    }
    if (onClick == null) {
        Row(
            modifier.fillMaxWidth().padding(horizontal = MacSpace.xl, vertical = MacSpace.lg),
            horizontalArrangement = Arrangement.spacedBy(MacSpace.lg),
            verticalAlignment = Alignment.CenterVertically,
            content = body,
        )
    } else {
        SelectableRow(selected, onClick, modifier, RoundedCornerShape(MacRadius.md), MacSpace.lg, MacSpace.lg, MacSpace.xl, body)
    }
}

/** The row every selectable list entry shares: hover fill, selection fill, focus ring, keyboard click. */
@Composable
internal fun SelectableRow(
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier,
    shape: RoundedCornerShape,
    vertical: Dp,
    gap: Dp = MacSpace.md,
    horizontal: Dp = MacSpace.lg,
    content: @Composable RowScope.() -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val state = interaction.controlState()
    Row(
        modifier
            .fillMaxWidth()
            .focusRing(state.focused, shape)
            .clip(shape)
            .background(rowFill(selected, state.hovered))
            .semantics { this.selected = selected }
            .clickable(interaction, null, role = Role.Tab, onClick = onClick)
            .padding(horizontal = horizontal, vertical = vertical),
        horizontalArrangement = Arrangement.spacedBy(gap),
        verticalAlignment = Alignment.CenterVertically,
        content = content,
    )
}

/** A hairline along the bottom edge, inside the bounds, as the file's bottom borders are. */
fun Modifier.bottomHairline(color: Color): Modifier =
    drawBehind {
        val stroke = MacSize.hairline.toPx()
        val y = size.height - stroke / 2
        drawLine(color, Offset(0f, y), Offset(size.width, y), stroke)
    }

/** A hairline along the top edge, inside the bounds. */
fun Modifier.topHairline(color: Color): Modifier =
    drawBehind {
        val stroke = MacSize.hairline.toPx()
        drawLine(color, Offset(0f, stroke / 2), Offset(size.width, stroke / 2), stroke)
    }

private val STATUS_DOT = 7.dp
private val BADGE_RING = 1.5.dp
private val BADGE_OVERHANG = 2.dp
