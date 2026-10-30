// The sizes and colours are the design system's own values; a name for each would only repeat it.
@file:Suppress("MagicNumber")

// The panels that open a page, and the small words and choices around them.

package pyrlyn.adwaita

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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.draw.innerShadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import pyrlyn.glass.Glyph
import pyrlyn.glass.GlyphIcon

/** The panel a page opens with: a release to look at, or the package the page is about. */
@Composable
fun TintedPanel(
    tint: PanelTint,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(
        modifier
            .fillMaxWidth()
            .dropShadow(Corners.Large, Shadow(radius = 40.dp, color = Color.Black.copy(alpha = tint.depth), offset = DpOffset(0.dp, 20.dp)))
            .clip(Corners.Large)
            .background(tint.brush)
            .innerShadow(Corners.Large, Shadow(radius = 0.dp, color = Color.White.copy(alpha = tint.highlight), offset = DpOffset(0.dp, 1.dp)))
            .border(1.dp, if (tint == PanelTint.Mint) Color.White.copy(alpha = 0.18f) else LocalAdwaita.current.line, Corners.Large),
        content = content,
    )
}

private val SCENE_CARD = cssGradient(145f, 0f to Color(0x42FFFFFF), 1f to Color(0x0FFFFFFF))

/**
 * The picture beside a hero: a [key] and its [value] over [lines] on a pane of glass, the [mark] on a dark sphere, and a
 * [tag] above them. Decorative: its words say what an install does, not what one did.
 */
@Composable
fun HeroScene(
    key: String,
    value: String,
    lines: List<String>,
    tag: String,
    modifier: Modifier = Modifier,
    mark: @Composable BoxScope.() -> Unit,
) {
    val c = LocalAdwaita.current
    val type = LocalAdwaitaType.current
    Box(modifier.size(280.dp, 260.dp)) {
        Column(
            Modifier
                .offset(y = 26.dp)
                .size(258.dp, 192.dp)
                .dropShadow(SceneShape, Shadow(radius = 50.dp, color = Color.Black.copy(alpha = 0.35f), offset = DpOffset(0.dp, 30.dp)))
                .clip(SceneShape)
                .background(SCENE_CARD)
                .innerShadow(SceneShape, Shadow(radius = 0.dp, color = Color.White.copy(alpha = 0.6f), offset = DpOffset(0.dp, 1.dp)))
                .border(1.dp, Color.White.copy(alpha = 0.38f), SceneShape)
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(9.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(key, color = c.accent, style = type.monoSmall, maxLines = 1)
                Text("→ $value", color = SCENE_INK, style = type.monoSmall, maxLines = 1)
            }
            for (line in lines) Text(line, color = SCENE_INK.copy(alpha = 0.74f), style = type.monoSmall, maxLines = 1)
        }
        Box(
            Modifier
                .align(Alignment.BottomEnd)
                .padding(bottom = 10.dp)
                .size(104.dp)
                .dropShadow(CircleShape, Shadow(radius = 44.dp, color = Color.Black.copy(alpha = 0.5f), offset = DpOffset(0.dp, 26.dp)))
                .clip(CircleShape)
                .drawBehind {
                    val light = Offset(size.width * 0.34f, size.height * 0.28f)
                    val stops = arrayOf(0f to Color(0xFF1F7D6F), 0.62f to Color(0xFF0A3D36), 1f to Color(0xFF062724))
                    drawRect(Brush.radialGradient(*stops, center = light, radius = size.width * 0.86f))
                }.innerShadow(CircleShape, Shadow(radius = 0.dp, color = Color.White.copy(alpha = 0.3f), offset = DpOffset(0.dp, 3.dp)))
                .innerShadow(CircleShape, Shadow(radius = 16.dp, color = Color.Black.copy(alpha = 0.4f), offset = DpOffset(0.dp, (-8).dp)))
                .border(1.dp, Color.White.copy(alpha = 0.16f), CircleShape),
            contentAlignment = Alignment.Center,
            content = mark,
        )
        Box(
            Modifier
                .align(Alignment.TopEnd)
                .padding(end = 22.dp)
                .heightIn(min = 32.dp)
                .background(c.update, CapsuleShape)
                .padding(horizontal = 14.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(tag, color = c.updateInk, style = type.mono.copy(fontWeight = FontWeight.Bold), maxLines = 1)
        }
    }
}

private val SceneShape = RoundedCornerShape(14.dp)
private val CapsuleShape = RoundedCornerShape(16.dp)
private val SCENE_INK = Color(0xEBECF6F3)

/** The small capitals over a title. */
@Composable
fun Eyebrow(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = Color(0xFF8FF0D2),
) {
    Text(text.uppercase(), color = color, style = LocalAdwaitaType.current.eyebrow, modifier = modifier)
}

/** What a list says when it has nothing in it. */
@Composable
fun EmptyNote(
    text: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text,
        color = LocalAdwaita.current.ink2,
        style = LocalAdwaitaType.current.body,
        textAlign = TextAlign.Center,
        modifier = modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 40.dp),
    )
}

/** One of a choice's rows. It goes in a [BoxedList]. */
@Composable
fun RadioRow(
    text: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val c = LocalAdwaita.current
    val source = remember { MutableInteractionSource() }
    val hovered by source.collectIsHoveredAsState()
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .background(if (hovered) c.hover else Color.Transparent)
            .focusRing(source, Corners.Medium)
            .hoverable(source)
            .clickable(source, null, role = Role.RadioButton, onClick = onClick)
            .semantics { this.selected = selected }
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            Modifier
                .size(20.dp)
                .clip(CircleShape)
                .background(if (selected) c.accent else c.fill2)
                .border(1.dp, if (selected) c.accent else c.line, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            if (selected) Box(Modifier.size(8.dp).background(c.accentInk, CircleShape))
        }
        Text(text, color = c.ink, style = LocalAdwaitaType.current.body, modifier = Modifier.weight(1f), maxLines = 1)
    }
}

/** A pill that shows the choice made and opens the others in a popover. */
@Composable
fun <T> Select(
    options: List<T>,
    selected: T,
    label: (T) -> String,
    modifier: Modifier = Modifier,
    optionModifier: (T) -> Modifier = { Modifier },
    onPick: (T) -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    Box {
        PillButton(label(selected), modifier, size = PillSize.Small, trailingGlyph = Glyph.ChevronDown) { open = true }
        if (open) {
            PopoverMenu({ open = false }) {
                for (option in options) {
                    MenuItem(
                        label(option),
                        optionModifier(option),
                        leading = { SelectedMark(option == selected) },
                    ) {
                        open = false
                        onPick(option)
                    }
                }
            }
        }
    }
}

@Composable
private fun SelectedMark(shown: Boolean) {
    Box(Modifier.size(16.dp)) {
        if (shown) GlyphIcon(Glyph.Check, LocalAdwaita.current.accentText, 16.dp)
    }
}
