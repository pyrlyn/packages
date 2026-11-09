package pyrlyn.macos

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** A full-width notice: restricted mode, a grant mismatch, a handover. [tone] is Info, Warning or Error. */
@Composable
fun Banner(
    text: String,
    modifier: Modifier = Modifier,
    tone: Tone = Tone.Info,
    icon: MacIcon = MacIcon.Document,
    action: (@Composable RowScope.() -> Unit)? = null,
) {
    val c = Mac.colors
    val (fill, ink) =
        when (tone) {
            Tone.Warning -> c.bannerWarningBg to c.bannerWarningInk
            Tone.Error -> c.bannerErrorBg to c.bannerErrorInk
            else -> c.bannerInfoBg to c.bannerInfoInk
        }
    Row(
        modifier
            .fillMaxWidth()
            .background(fill)
            .bottomHairline(c.borderSubtle)
            .padding(horizontal = MacSpace.xxl, vertical = MacSpace.lg),
        horizontalArrangement = Arrangement.spacedBy(MacSpace.lg),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, tint = ink)
        Label(text, Modifier.weight(1f), ink, Mac.type.small)
        action?.invoke(this)
    }
}

/** A chat message: the sender's name above the body. A user's message sits on a fill; an assistant's does not. */
@Composable
fun Message(
    author: String,
    fromUser: Boolean,
    modifier: Modifier = Modifier,
    body: @Composable ColumnScope.() -> Unit,
) {
    val c = Mac.colors
    val shape = RoundedCornerShape(MacRadius.md)
    Column(
        modifier
            .fillMaxWidth()
            .then(
                if (fromUser) {
                    Modifier.background(c.bgFill, shape).padding(horizontal = MacSpace.xl, vertical = MacSpace.lg)
                } else {
                    Modifier
                },
            ),
        verticalArrangement = Arrangement.spacedBy(MacSpace.xs),
    ) {
        Label(author, color = if (fromUser) c.textSecondary else c.accent, style = Mac.type.captionBold)
        ProvideMacContent(c.textPrimary, Mac.type.body) { body() }
    }
}

/** A chat message whose body is plain text. */
@Composable
fun Message(
    author: String,
    text: String,
    fromUser: Boolean,
    modifier: Modifier = Modifier,
) {
    Message(author, fromUser, modifier) { Label(text) }
}

/** A command the agent ran, its state chip and its output on the terminal ground. */
@Composable
fun CommandCard(
    command: String,
    state: String,
    tone: Tone,
    modifier: Modifier = Modifier,
    output: String? = null,
) {
    val c = Mac.colors
    val shape = RoundedCornerShape(MacRadius.md)
    Column(
        modifier
            .fillMaxWidth()
            .clip(shape)
            .background(c.bgTerminal)
            .border(MacSize.hairline, c.borderSubtle, shape),
    ) {
        Row(
            Modifier.fillMaxWidth().background(c.bgTerminalChrome).padding(horizontal = MacSpace.xl, vertical = MacSpace.md),
            horizontalArrangement = Arrangement.spacedBy(MacSpace.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(MacIcon.Terminal)
            Label(command, Modifier.weight(1f), c.textTerminal, Mac.type.code, maxLines = 1)
            Chip(state, tone = tone)
        }
        output?.let {
            Box(Modifier.fillMaxWidth().padding(horizontal = MacSpace.xl, vertical = MacSpace.lg)) {
                Label(it, color = c.textTerminalDim, style = Mac.type.code)
            }
        }
    }
}

/** A rule with a label and an optional action between items in a list or timeline. */
@Composable
fun LabeledDivider(
    label: String,
    modifier: Modifier = Modifier,
    action: String? = null,
    onAction: () -> Unit = {},
) {
    val c = Mac.colors
    Row(
        modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(MacSpace.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Rule(Modifier.weight(1f))
        Label(label, color = c.textTertiary, style = Mac.type.caption, maxLines = 1)
        action?.let {
            Label(it, Modifier.clickable(role = Role.Button, onClick = onAction), c.accent, Mac.type.caption, maxLines = 1)
        }
        Rule(Modifier.weight(1f))
    }
}

/** A collapsed disclosure line such as "Thinking · 14 s"; [onClick] expands it. */
@Composable
fun Disclosure(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier.clickable(role = Role.Button, onClick = onClick),
        horizontalArrangement = Arrangement.spacedBy(MacSpace.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(MacIcon.ChevronRight)
        Label(label, color = Mac.colors.textTertiary, style = Mac.type.small, maxLines = 1)
    }
}

/**
 * The message composer: a raised card with the text on top and a row of controls under it. [actions] sit at the
 * leading edge (attach, permission), [trailing] before the round send button (the model or agent picker).
 */
@Composable
fun Composer(
    value: String,
    onValueChange: (String) -> Unit,
    onSend: () -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    hint: String? = null,
    sendLabel: String = "Send",
    sendEnabled: Boolean = value.isNotBlank(),
    minHeight: Dp = COMPOSER_TEXT,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    trailing: @Composable RowScope.() -> Unit = {},
    actions: @Composable RowScope.() -> Unit = {},
) {
    val c = Mac.colors
    Column(
        modifier
            .fillMaxWidth()
            .raisedSurface(RoundedCornerShape(MacRadius.card))
            .padding(start = MacSpace.lg, end = MacSpace.lg, top = MacSpace.xxl, bottom = MacSpace.lg),
        verticalArrangement = Arrangement.spacedBy(MacSpace.lg),
    ) {
        ComposerText(value, onValueChange, placeholder, minHeight, keyboardActions, Modifier.padding(horizontal = MacSpace.xs))
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(MacSpace.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            actions()
            Box(Modifier.weight(1f).height(MacSize.hairline))
            hint?.let { Label(it, Modifier.padding(end = MacSpace.xs), c.textTertiary, Mac.type.caption, maxLines = 1) }
            trailing()
            SendButton(onSend, enabled = sendEnabled, description = sendLabel)
        }
    }
}

private val COMPOSER_TEXT = 40.dp
