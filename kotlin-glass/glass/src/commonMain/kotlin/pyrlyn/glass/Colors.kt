// The colours are the design's own hex values; a name for each would only repeat it.
@file:Suppress("MagicNumber")

// The look's colours. The consumer fills the semantic slots from its own tokens; the panes' gradients and shadows are
// the look itself, so they live here.

package pyrlyn.glass

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp

/** The kinds of glass, each with its corners. */
enum class Pane(
    val radius: Dp,
) {
    Sidebar(20.dp),
    Card(20.dp),

    /** A [Card] that hugs the wallpaper: long text and lists. */
    Flat(20.dp),
    Tile(20.dp),
    Row(18.dp),

    /** A bar floating over the content. */
    Floating(22.dp),
    Sheet(26.dp),
}

/** One layer of a shadow, in CSS's terms. */
@Immutable
data class Lift(
    val blur: Dp,
    val y: Dp,
    val color: Color,
) {
    val offset get() = DpOffset(0.dp, y)
}

/** How one [Pane] is drawn in this appearance. */
@Immutable
data class PaneLook(
    val top: Color,
    val bottom: Color,
    val rim: Color,
    val lift: List<Lift>,
)

/**
 * The colours of one appearance. [tint] washes every pane, [clear] thins the glass and [wash] sets how strongly the
 * wallpaper shows, 1 being the standard.
 */
@Immutable
data class GlassColors(
    val dark: Boolean,
    val ink: Color,
    val ink2: Color,
    val accent: Color,
    val accentPressed: Color,
    val accentSubtle: Color,
    val accentOn: Color,
    val accentInk: Color,
    val success: Color,
    val successSubtle: Color,
    val attention: Color,
    val attentionSubtle: Color,
    val attentionFill: Color,
    val attentionOn: Color,
    val progress: Color,
    val progressSubtle: Color,
    val warning: Color,
    val warningSubtle: Color,
    val danger: Color,
    val dangerSubtle: Color,
    val base: Color,
    val control: Color,
    val highlight: Color,
    val shade: Color,
    val solid: Color,
    val separator: Color,
    val fill: Color,
    val hover: Color,
    val track: Color,
    val focus: Color,
    val scrim: Color,
    val tint: Color? = null,
    val tintOpacity: Float = 0f,
    val clear: Boolean = false,
    val wash: Float = 1f,
) {
    val accentTop = Color.White.copy(alpha = ACCENT_SHEEN).compositeOver(accent)

    /** The window body over the wallpaper. */
    val windowStops = if (dark) listOf(Color(0x731A1E28), Color(0x471A1E28), Color(0x591A1E28)) else listOf(Color(0x4DFFFFFF), Color(0x1AFFFFFF), Color(0x2EFFFFFF))

    /** A raised white control: [ButtonKind.Raised] and the selected segment. */
    val raised = if (dark) listOf(Color(0x2EFFFFFF), Color(0x2EFFFFFF)) else listOf(Color.White, Color(0xDBFFFFFF))
    val raisedLift = if (dark) emptyList() else listOf(Lift(0.dp, 1.dp, Color(0x140E2838)), Lift(6.dp, 3.dp, Color(0x1F0E2838)))
    val hairline = if (dark) Color(0x1FFFFFFF) else Color(0x140E2838)

    /** The selected sidebar row. */
    val active = if (dark) listOf(Color(0x1FFFFFFF), Color(0x1FFFFFFF)) else listOf(Color(0xB3FFFFFF), Color(0x80FFFFFF))
    val activeLift = if (dark) listOf(Lift(6.dp, 3.dp, Color(0x59000000))) else listOf(Lift(0.dp, 1.dp, Color(0x120E2838)), Lift(6.dp, 3.dp, Color(0x140E2838)))

    /** The toolbar's capsules: the arrows and the search field. */
    val pill = if (dark) Color(0x14FFFFFF) else Color(0x80FFFFFF)
    val pillRim = if (dark) Color(0x29FFFFFF) else Color(0xCCFFFFFF)

    // Compose cannot blur what lies behind a surface, so what floats over content stays nearly opaque to keep it legible.
    fun pane(pane: Pane): PaneLook = if (dark) darkPane(pane) else lightPane(pane)

    private fun lightPane(pane: Pane) =
        when (pane) {
            Pane.Sidebar -> PaneLook(Color(0x94FFFFFF), Color(0x4DFFFFFF), Color(0xD9FFFFFF), listOf(Lift(48.dp, 24.dp, Color(0x2E0E2838)), Lift(0.dp, 4.dp, Color(0x0F0E2838))))
            Pane.Card -> PaneLook(Color(0x8FFFFFFF), Color(0x52FFFFFF), Color(0xE6FFFFFF), LIFT_2)
            Pane.Flat -> PaneLook(Color(0x8FFFFFFF), Color(0x52FFFFFF), Color(0xE6FFFFFF), LIFT_1)
            Pane.Tile -> PaneLook(Color(0x99FFFFFF), Color(0x57FFFFFF), Color(0xEBFFFFFF), LIFT_2)
            Pane.Row -> PaneLook(Color(0x80FFFFFF), Color(0x4DFFFFFF), Color(0xD9FFFFFF), LIFT_1)
            Pane.Floating -> PaneLook(Color(0xF7FFFFFF), Color(0xEBFFFFFF), Color(0xF2FFFFFF), LIFT_3)
            Pane.Sheet -> PaneLook(Color(0xFCFFFFFF), Color(0xF2FFFFFF), Color.White, listOf(Lift(80.dp, 40.dp, Color(0x520E2838)), Lift(20.dp, 8.dp, Color(0x260E2838))))
        }

    private fun darkPane(pane: Pane) =
        when (pane) {
            Pane.Sidebar -> PaneLook(Color(0x99292E3A), Color(0x59292E3A), Color(0x29FFFFFF), listOf(Lift(48.dp, 24.dp, Color(0x73000000))))
            Pane.Card, Pane.Tile -> PaneLook(Color(0x8C292E3A), Color(0x59292E3A), Color(0x1FFFFFFF), listOf(Lift(34.dp, 18.dp, Color(0x66000000))))
            Pane.Row, Pane.Flat -> PaneLook(Color(0x73292E3A), Color(0x73292E3A), Color(0x1FFFFFFF), listOf(Lift(12.dp, 4.dp, Color(0x59000000))))
            Pane.Floating -> PaneLook(Color(0xF2292E3A), Color(0xF2292E3A), Color(0x29FFFFFF), listOf(Lift(50.dp, 24.dp, Color(0x80000000))))
            Pane.Sheet -> PaneLook(Color(0xFA292E3A), Color(0xFA292E3A), Color(0x29FFFFFF), listOf(Lift(80.dp, 40.dp, Color(0x80000000))))
        }

    companion object {
        /** The deep green of the hero and of ink on light glass tags. */
        val Deep = Color(0xFF0A3D36)
        val Mint = Color(0xFF3DDCB0)
        val Coral = Color(0xFFFF6B5B)

        /** A terminal's ink, for commands over a dark fill. */
        val TerminalInk = Color(0xFFE8F4F1)

        private const val ACCENT_SHEEN = 0.16f
        private val LIFT_1 = listOf(Lift(2.dp, 1.dp, Color(0x0F0D1624)), Lift(12.dp, 4.dp, Color(0x0D0D1624)))
        private val LIFT_2 = listOf(Lift(34.dp, 18.dp, Color(0x260E2838)), Lift(0.dp, 4.dp, Color(0x120E2838)))
        private val LIFT_3 = listOf(Lift(50.dp, 24.dp, Color(0x380E2838)), Lift(10.dp, 4.dp, Color(0x140E2838)))
    }
}

val LocalGlass = staticCompositionLocalOf<GlassColors> { error("GlassTheme is not applied") }

/** [colors] for the components below, and over the Material theme, whose menus and sliders they lean on. */
@Composable
fun GlassTheme(
    colors: GlassColors,
    content: @Composable () -> Unit,
) {
    val scheme =
        MaterialTheme.colorScheme.copy(
            primary = colors.accent,
            onPrimary = colors.accentOn,
            background = colors.base,
            onBackground = colors.ink,
            surface = colors.solid,
            surfaceContainer = colors.solid,
            onSurface = colors.ink,
            onSurfaceVariant = colors.ink2,
        )
    CompositionLocalProvider(LocalGlass provides colors) {
        MaterialTheme(colorScheme = scheme, typography = MaterialTheme.typography, shapes = MaterialTheme.shapes, content = content)
    }
}
