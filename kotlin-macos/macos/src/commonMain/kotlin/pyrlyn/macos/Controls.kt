package pyrlyn.macos

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

/** The Button component's styles. */
enum class ButtonStyle {
    /** The accent pill: the one action a screen is for. */
    Primary,

    /** The neutral filled pill. */
    Secondary,

    /** A hairline pill, for a setting shown as a button ("Permissions: Auto"). */
    Outline,

    /** The error-ink outline: stop, remove, discard. */
    DestructiveOutline,

    /** No fill until the pointer is over it. */
    Ghost,
}

/** Regular pills sit in toolbars and headers; Large ones close sheets. */
enum class ButtonSize { Regular, Large }

/** The file's two single-line fields: the 44-point sheet field and the 32-point inline one. */
enum class FieldSize { Large, Regular }

/** A pill button with an optional leading icon. */
@Composable
fun Button(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    style: ButtonStyle = ButtonStyle.Secondary,
    size: ButtonSize = ButtonSize.Regular,
    icon: MacIcon? = null,
    enabled: Boolean = true,
) {
    Button(onClick, modifier, style, size, enabled) {
        icon?.let { Icon(it, tint = LocalMacContentColor.current) }
        Label(text, maxLines = 1)
    }
}

/** A pill button around any content, which reads its colour and style from [LocalMacContentColor] and [LocalMacTextStyle]. */
@Composable
fun Button(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    style: ButtonStyle = ButtonStyle.Secondary,
    size: ButtonSize = ButtonSize.Regular,
    enabled: Boolean = true,
    content: @Composable RowScope.() -> Unit,
) {
    val c = Mac.colors
    val ink =
        when (style) {
            ButtonStyle.Primary -> c.textOnAccent
            ButtonStyle.DestructiveOutline -> c.statusError
            else -> c.textPrimary
        }
    val fill =
        when (style) {
            ButtonStyle.Primary -> c.accent
            ButtonStyle.Secondary -> c.bgFillStrong
            else -> Color.Transparent
        }
    val border =
        when (style) {
            ButtonStyle.Outline -> c.borderStrong
            ButtonStyle.DestructiveOutline -> c.statusError
            else -> null
        }
    val large = size == ButtonSize.Large
    val interaction = remember { MutableInteractionSource() }
    Row(
        modifier
            .height(if (large) MacSize.buttonLarge else MacSize.buttonRegular)
            .control(CircleShape, fill, border, interaction, enabled, Role.Button, onClick)
            .padding(horizontal = if (large) MacSpace.xxl else MacSpace.lg),
        horizontalArrangement = Arrangement.spacedBy(MacSpace.sm, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ProvideMacContent(ink, if (large) Mac.type.bodyEmphasized else Mac.type.smallEmphasized) { content() }
    }
}

/** A round 28-point button holding one icon; [description] is what a screen reader says. */
@Composable
fun IconButton(
    icon: MacIcon,
    description: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color = Mac.colors.textSecondary,
    enabled: Boolean = true,
) {
    val interaction = remember { MutableInteractionSource() }
    Box(
        modifier
            .size(MacSize.iconButton)
            .semantics { contentDescription = description }
            .control(CircleShape, Color.Transparent, null, interaction, enabled, Role.Button, onClick),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, tint = tint) }
}

/**
 * A borderless picker that reads as text: an optional [icon], the [value], a dimmer [detail] such as the effort,
 * and a chevron. [tint] colours the icon and value, for a choice that needs a warning; [filled] puts it on a capsule.
 */
@Composable
fun PickerButton(
    value: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    detail: String? = null,
    icon: MacIcon? = null,
    tint: Color? = null,
    filled: Boolean = false,
    enabled: Boolean = true,
) {
    val c = Mac.colors
    val interaction = remember { MutableInteractionSource() }
    val fill = if (filled) c.bgFillMedium else Color.Transparent
    Row(
        modifier
            .height(MacSize.picker)
            .control(CircleShape, fill, null, interaction, enabled, Role.DropdownList, onClick)
            .padding(horizontal = MacSpace.md),
        horizontalArrangement = Arrangement.spacedBy(MacSpace.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        icon?.let { Icon(it, tint = tint ?: c.textSecondary) }
        Label(value, color = tint ?: c.textPrimary, style = Mac.type.body, maxLines = 1)
        detail?.let { Label(it, color = c.textTertiary, style = Mac.type.body, maxLines = 1) }
        Icon(MacIcon.ChevronDown, tint = tint ?: c.textTertiary, size = CHEVRON)
    }
}

/** The composer's round send button: an up arrow on the primary ink, grey while there is nothing to send. */
@Composable
fun SendButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    description: String = "Send",
) {
    val c = Mac.colors
    val interaction = remember { MutableInteractionSource() }
    Box(
        modifier
            .size(MacSize.sendButton)
            .semantics { contentDescription = description }
            // Not the shared control(): a disabled send keeps full opacity and swaps to the grey fill instead.
            .focusRing(interaction.controlState().focused, CircleShape)
            .clip(CircleShape)
            .background(if (enabled) c.textPrimary else c.bgFillStrong)
            .clickable(interaction, null, enabled, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Icon(MacIcon.ArrowUp, tint = if (enabled) c.bgChrome.copy(alpha = 1f) else c.textTertiary) }
}

/** A status capsule: a word in [tone]'s ink on its tint, with an optional dot. */
@Composable
fun Chip(
    label: String,
    modifier: Modifier = Modifier,
    tone: Tone = Tone.Ok,
    dot: Boolean = false,
) {
    val ink = tone.ink()
    Row(
        modifier.height(MacSize.chip).background(tone.tint(), CircleShape).padding(horizontal = MacSpace.md),
        horizontalArrangement = Arrangement.spacedBy(MacSpace.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (dot) Box(Modifier.size(CHIP_DOT).background(ink, CircleShape))
        Label(label, color = ink, style = Mac.type.captionBold, maxLines = 1)
    }
}

/** The on-off switch, 32 × 18. */
@Composable
fun Switch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val c = Mac.colors
    val interaction = remember { MutableInteractionSource() }
    val state = interaction.controlState()
    val shape = CircleShape
    Box(
        modifier
            .size(MacSize.switchWidth, MacSize.switchHeight)
            .focusRing(state.focused, shape)
            .clip(shape)
            .background(if (checked) c.accent else c.bgSwitchOff)
            .toggleable(checked, interaction, null, enabled, Role.Switch, onCheckedChange)
            .alpha(if (enabled) 1f else DISABLED_ALPHA)
            .padding(KNOB_INSET),
        contentAlignment = if (checked) Alignment.CenterEnd else Alignment.CenterStart,
    ) {
        Box(Modifier.size(KNOB).background(Color.White, CircleShape))
    }
}

/** A pop-up button showing [value]; [onClick] opens the choices. */
@Composable
fun PopUpButton(
    value: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val interaction = remember { MutableInteractionSource() }
    Row(
        modifier
            .height(MacSize.popUp)
            .control(RoundedCornerShape(MacRadius.md), Mac.colors.bgFillMedium, null, interaction, enabled, Role.DropdownList, onClick)
            .padding(horizontal = MacSpace.xxl),
        horizontalArrangement = Arrangement.spacedBy(MacSpace.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Label(value, Modifier.weight(1f), Mac.colors.textPrimary, Mac.type.body, maxLines = 1)
        Box(Modifier.size(CHEVRONS_WIDTH, CHEVRONS_HEIGHT)) {
            Icon(MacIcon.Chevrons, Modifier.wrapContentSize(Alignment.TopStart, unbounded = true))
        }
    }
}

/** A pop-up button over [options] that opens its own menu and reports the pick. */
@Composable
fun <T> PopUpButton(
    selected: T,
    options: List<T>,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    label: (T) -> String = { it.toString() },
) {
    var open by remember { mutableStateOf(false) }
    Box(modifier) {
        PopUpButton(label(selected), { open = true }, Modifier.fillMaxWidth(), enabled)
        Menu(open, { open = false }) {
            for (option in options) {
                MenuItem(label(option), onClick = {
                    open = false
                    onSelect(option)
                }, checked = option == selected)
            }
        }
    }
}

/** A labelled control: the Small-emphasized label above, 6 points apart. */
@Composable
fun Field(
    label: String,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(MacSpace.sm)) {
        Label(label, color = Mac.colors.textSecondary, style = Mac.type.smallEmphasized)
        content()
    }
}

/** A setting's title, the sentence under it, and its control at the trailing edge. */
@Composable
fun ControlRow(
    title: String,
    modifier: Modifier = Modifier,
    detail: String? = null,
    control: @Composable () -> Unit,
) {
    Row(
        modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(MacSpace.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(MacSpace.xxs)) {
            Label(title, color = Mac.colors.textPrimary, style = Mac.type.bodyEmphasized)
            detail?.let { Label(it, color = Mac.colors.textSecondary, style = Mac.type.small) }
        }
        control()
    }
}

/** The background, border, hover veil, focus ring and click every control in the library shares. */
@Composable
internal fun Modifier.control(
    shape: Shape,
    fill: Color,
    border: Color?,
    interaction: MutableInteractionSource,
    enabled: Boolean,
    role: Role,
    onClick: () -> Unit,
): Modifier {
    val state = interaction.controlState()
    val veil = if (enabled) state.veil() else null
    return this
        .focusRing(state.focused, shape)
        .clip(shape)
        .background(fill)
        .then(if (veil != null) Modifier.background(veil) else Modifier)
        .then(if (border != null) Modifier.border(MacSize.hairline, border, shape) else Modifier)
        .clickable(interaction, null, enabled, role = role, onClick = onClick)
        .alpha(if (enabled) 1f else DISABLED_ALPHA)
}

/** The neutral hover surface of a row in a list, sidebar or menu. */
@Composable
internal fun rowFill(
    selected: Boolean,
    hovered: Boolean,
): Color =
    when {
        selected -> Mac.colors.bgSelection
        hovered -> Mac.colors.bgFill
        else -> Color.Transparent
    }

private val CHIP_DOT = 6.dp
private val CHEVRON = 12.dp
private val KNOB = 14.dp
private val KNOB_INSET = 2.dp
private val CHEVRONS_WIDTH = 10.dp
private val CHEVRONS_HEIGHT = 14.dp
