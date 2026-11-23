package pyrlyn.macos

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** A single-line text field. */
@Composable
fun TextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    size: FieldSize = FieldSize.Large,
    enabled: Boolean = true,
    mono: Boolean = false,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    leading: (@Composable () -> Unit)? = null,
) {
    val c = Mac.colors
    val large = size == FieldSize.Large
    val style =
        if (mono) {
            Mac.type.code
        } else if (large) {
            Mac.type.field
        } else {
            Mac.type.body
        }
    FieldFrame(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.height(if (large) MacSize.field else MacSize.popUp),
        style = style,
        placeholder = placeholder,
        placeholderColor = if (large) c.textSecondary else c.textTertiary,
        fill = if (large) c.bgFillStrong else c.bgFillMedium,
        border = if (large) c.borderSubtle else null,
        radius = if (large) MacRadius.field else MacRadius.md,
        horizontal = if (large) FIELD_INSET else MacSpace.xxl,
        vertical = 0.dp,
        singleLine = true,
        enabled = enabled,
        visualTransformation = visualTransformation,
        keyboardOptions = keyboardOptions,
        keyboardActions = keyboardActions,
        leading = leading,
        trailing = null,
    )
}

/** A multi-line field, such as a first prompt or a summary. */
@Composable
fun TextArea(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    minHeight: Dp = TEXT_AREA_HEIGHT,
    enabled: Boolean = true,
    mono: Boolean = false,
) {
    val c = Mac.colors
    FieldFrame(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.defaultMinSize(minHeight = minHeight),
        style = if (mono) Mac.type.code else Mac.type.body,
        placeholder = placeholder,
        placeholderColor = c.textTertiary,
        fill = c.bgFill,
        border = c.borderSubtle,
        radius = MacRadius.md,
        horizontal = MacSpace.xl,
        vertical = MacSpace.lg,
        singleLine = false,
        enabled = enabled,
        visualTransformation = VisualTransformation.None,
        keyboardOptions = KeyboardOptions.Default,
        keyboardActions = KeyboardActions.Default,
        leading = null,
        trailing = null,
    )
}

/** The toolbar search field with its shortcut hint. */
@Composable
fun SearchField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "Search",
    shortcut: String? = null,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
) {
    val c = Mac.colors
    FieldFrame(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.height(MacSize.searchField),
        style = Mac.type.small,
        placeholder = placeholder,
        placeholderColor = c.textTertiary,
        fill = c.bgFill,
        border = null,
        radius = MacRadius.sm,
        horizontal = MacSpace.lg,
        vertical = 0.dp,
        singleLine = true,
        enabled = true,
        visualTransformation = VisualTransformation.None,
        keyboardOptions = KeyboardOptions.Default,
        keyboardActions = keyboardActions,
        leading = { Icon(MacIcon.Search) },
        trailing = shortcut?.let { { Label(it, color = c.textTertiary, style = Mac.type.caption) } },
    )
}

/** The composer's own text: no fill or border of its own. */
@Composable
internal fun ComposerText(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    minHeight: Dp,
    keyboardActions: KeyboardActions,
    modifier: Modifier = Modifier,
) {
    FieldFrame(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.fillMaxWidth().defaultMinSize(minHeight = minHeight),
        style = Mac.type.body,
        placeholder = placeholder,
        placeholderColor = Mac.colors.textTertiary,
        fill = Color.Transparent,
        border = null,
        radius = 0.dp,
        horizontal = 0.dp,
        vertical = 0.dp,
        singleLine = false,
        enabled = true,
        visualTransformation = VisualTransformation.None,
        keyboardOptions = KeyboardOptions.Default,
        keyboardActions = keyboardActions,
        leading = null,
        trailing = null,
    )
}

/** The search field's look as a button, for a toolbar search that opens a palette instead of taking text. */
@Composable
fun SearchButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "Search",
    shortcut: String? = null,
) {
    val c = Mac.colors
    val interaction = remember { MutableInteractionSource() }
    Row(
        modifier
            .height(MacSize.searchField)
            .control(RoundedCornerShape(MacRadius.sm), c.bgFill, null, interaction, true, Role.Button, onClick)
            .padding(horizontal = MacSpace.lg),
        horizontalArrangement = Arrangement.spacedBy(MacSpace.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(MacIcon.Search)
        Label(placeholder, Modifier.weight(1f), c.textTertiary, Mac.type.small, maxLines = 1)
        shortcut?.let { Label(it, color = c.textTertiary, style = Mac.type.caption) }
    }
}

@Suppress("LongParameterList")
@Composable
private fun FieldFrame(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier,
    style: TextStyle,
    placeholder: String,
    placeholderColor: Color,
    fill: Color,
    border: Color?,
    radius: Dp,
    horizontal: Dp,
    vertical: Dp,
    singleLine: Boolean,
    enabled: Boolean,
    visualTransformation: VisualTransformation,
    keyboardOptions: KeyboardOptions,
    keyboardActions: KeyboardActions,
    leading: (@Composable () -> Unit)?,
    trailing: (@Composable () -> Unit)?,
) {
    val c = Mac.colors
    val interaction = remember { MutableInteractionSource() }
    val state = interaction.controlState()
    val shape = RoundedCornerShape(radius)
    // A borderless field (the composer's) is framed by its container, which shows focus itself.
    val edge = if (state.focused && border != null) c.accent else border
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        modifier =
            modifier
                .background(fill, shape)
                .then(if (edge != null) Modifier.border(MacSize.hairline, edge, shape) else Modifier)
                .alpha(if (enabled) 1f else DISABLED_ALPHA),
        enabled = enabled,
        textStyle = style.copy(color = c.textPrimary),
        cursorBrush = SolidColor(c.accent),
        singleLine = singleLine,
        visualTransformation = visualTransformation,
        keyboardOptions = keyboardOptions,
        keyboardActions = keyboardActions,
        interactionSource = interaction,
        decorationBox = { inner ->
            Row(
                Modifier.fillMaxWidth().padding(horizontal = horizontal, vertical = vertical),
                horizontalArrangement = Arrangement.spacedBy(MacSpace.md),
                verticalAlignment = if (singleLine) Alignment.CenterVertically else Alignment.Top,
            ) {
                leading?.invoke()
                Box(Modifier.weight(1f)) {
                    if (value.isEmpty()) {
                        Label(placeholder, color = placeholderColor, style = style, maxLines = if (singleLine) 1 else Int.MAX_VALUE)
                    }
                    inner()
                }
                trailing?.invoke()
            }
        },
    )
}

private val FIELD_INSET = 13.dp
private val TEXT_AREA_HEIGHT = 120.dp
