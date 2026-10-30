// The colours are the design system's own values; a name for each would only repeat it.
@file:Suppress("MagicNumber")

// The Pyrlyn Windows 11 palette, light and dark. A consumer picks the appearance, the accent, the window material and
// the tint; every other colour belongs to the design system.

package pyrlyn.fluent

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/** What the window lets through, thickest first. */
enum class WindowMaterial { Mica, Acrylic, Transparent }

/** A colour with the name it is stored and read out by. */
@Immutable
data class Preset(
    val name: String,
    val color: Color,
)

/** The design system's Appearance choices. */
object Presets {
    val tints =
        listOf(
            Preset("Sky", Color(0xFF7CC4FF)),
            Preset("Mint", Color(0xFF6FE3C1)),
            Preset("Sand", Color(0xFFFFC98B)),
            Preset("Rose", Color(0xFFFF9EC0)),
            Preset("Lilac", Color(0xFFB9A6FF)),
            Preset("Smoke", Color(0xFF5B6577)),
        )
    val accents =
        listOf(
            Preset("Navy", Color(0xFF0063B1)),
            Preset("Purple", Color(0xFF8764B8)),
            Preset("Pink", Color(0xFFE3008C)),
            Preset("Red", Color(0xFFC42B1C)),
            Preset("Orange", Color(0xFFCA5010)),
            Preset("Green", Color(0xFF107C10)),
            Preset("Graphite", Color(0xFF68768A)),
        )

    /** The accent the design system stands on, which the Automatic swatch shows. */
    val standardAccent = Color(0xFF0873E0)
}

/** The colours of one appearance. [wash] is how strongly [tint], or the window's own colour without one, covers the wall. */
@Immutable
data class FluentColors(
    val dark: Boolean,
    val ink: Color,
    val ink2: Color,
    val accent: Color,
    val accentHover: Color,
    val accentPressed: Color,
    val accentOn: Color,
    val accentInk: Color,
    val windowMica: Color,
    val windowAcrylic: Color,
    val windowClear: Color,
    val layer: Color,
    val card: Color,
    val cardHover: Color,
    val flyout: Color,
    val dialog: Color,
    val dialogFoot: Color,
    val smoke: Color,
    val stroke: Color,
    val strokeWindow: Color,
    val rim: Color,
    val control: Color,
    val controlHover: Color,
    val controlBottom: Color,
    val hover: Color,
    val selected: Color,
    val success: Color,
    val successSubtle: Color,
    val update: Color,
    val updateOn: Color,
    val updateInk: Color,
    val updateSubtle: Color,
    val warning: Color,
    val warningSubtle: Color,
    val danger: Color,
    val dangerSubtle: Color,
    val busy: Color,
    val infoSubtle: Color,
    val track: Color,
    val thumbRing: Color,
    val wallBase: Color,
    val wallOrbs: List<Color>,
    val petalTop: Color,
    val petalBottom: Color,
    /** What a menu sits on: opaque, as Compose cannot blur what lies behind it. */
    val solid: Color,
    val material: WindowMaterial = WindowMaterial.Acrylic,
    val tint: Color? = null,
    val wash: Float = STANDARD_WASH,
) {
    val window
        get() =
            when (material) {
                WindowMaterial.Mica -> windowMica
                WindowMaterial.Acrylic -> windowAcrylic
                WindowMaterial.Transparent -> windowClear
            }

    /** What covers the wall under the window's material. */
    val washLayer get() = (tint ?: window).copy(alpha = wash)

    /** This palette with [accent] in place of the design system's. */
    fun withAccent(accent: Color) =
        copy(
            accent = accent,
            accentHover = Color.White.copy(alpha = ACCENT_STEP).compositeOver(accent),
            accentPressed = Color.Black.copy(alpha = ACCENT_STEP).compositeOver(accent),
            accentOn = if (accent.luminance() > LIGHT_ACCENT) ON_LIGHT_ACCENT else Color.White,
            accentInk = accent,
        )

    companion object {
        const val STANDARD_WASH = 0.14f
        private const val ACCENT_STEP = 0.1f
        private const val LIGHT_ACCENT = 0.5f
        private val ON_LIGHT_ACCENT = Color(0xFF00182F)

        val Light =
            FluentColors(
                dark = false,
                ink = Color(0xFF0D1B2A),
                ink2 = Color(0xFF4A5A70),
                accent = Color(0xFF0873E0),
                accentHover = Color(0xFF1F80E3),
                accentPressed = Color(0xFF0663C2),
                accentOn = Color.White,
                accentInk = Color(0xFF05509C),
                windowMica = Color(236, 242, 250, 230),
                windowAcrylic = Color(240, 245, 251, 153),
                windowClear = Color(244, 248, 253, 87),
                layer = Color(255, 255, 255, 128),
                card = Color(255, 255, 255, 153),
                cardHover = Color(255, 255, 255, 204),
                flyout = Color(250, 252, 255, 240),
                dialog = Color(255, 255, 255, 240),
                dialogFoot = Color(240, 245, 251, 240),
                smoke = Color(0, 10, 30, 77),
                stroke = Color(0, 30, 80, 23),
                strokeWindow = Color(0, 30, 80, 41),
                rim = Color(255, 255, 255, 217),
                control = Color(255, 255, 255, 184),
                controlHover = Color(250, 251, 253, 235),
                controlBottom = Color(0, 30, 80, 51),
                hover = Color(0, 30, 80, 14),
                selected = Color(0, 30, 80, 19),
                success = Color(0xFF12683A),
                successSubtle = Color(31, 157, 87, 38),
                update = Color(0xFFC25A06),
                updateOn = Color.White,
                updateInk = Color(0xFF9A3C06),
                updateSubtle = Color(240, 123, 31, 41),
                warning = Color(0xFF774B00),
                warningSubtle = Color(224, 161, 0, 46),
                danger = Color(0xFFA81E34),
                dangerSubtle = Color(224, 49, 75, 33),
                busy = Color(0xFF4F3FC0),
                infoSubtle = Color(10, 132, 255, 33),
                track = Color(0, 30, 80, 36),
                thumbRing = Color.White,
                wallBase = Color(0xFFE4EEFA),
                wallOrbs = listOf(Color(0, 198, 255, 153), Color(0, 229, 160, 128), Color(0, 114, 255, 153)),
                petalTop = Color(0xFF00C6FF),
                petalBottom = Color(0xFF0072FF),
                solid = Color(0xFFFAFCFF),
            )

        val Dark =
            FluentColors(
                dark = true,
                ink = Color(0xFFEEF2F8),
                ink2 = Color(0xFFA3ADBF),
                accent = Color(0xFF6CB6FF),
                accentHover = Color(0xFF82C1FF),
                accentPressed = Color(0xFF5AA6F0),
                accentOn = ON_LIGHT_ACCENT,
                accentInk = Color(0xFF6CB6FF),
                windowMica = Color(30, 33, 42, 237),
                windowAcrylic = Color(28, 31, 40, 168),
                windowClear = Color(24, 27, 36, 92),
                layer = Color(255, 255, 255, 9),
                card = Color(255, 255, 255, 14),
                cardHover = Color(255, 255, 255, 23),
                flyout = Color(40, 44, 54, 245),
                dialog = Color(43, 46, 56, 245),
                dialogFoot = Color(32, 34, 42, 245),
                smoke = Color(0, 0, 0, 115),
                stroke = Color(255, 255, 255, 20),
                strokeWindow = Color(255, 255, 255, 36),
                rim = Color(255, 255, 255, 26),
                control = Color(255, 255, 255, 17),
                controlHover = Color(255, 255, 255, 26),
                controlBottom = Color(255, 255, 255, 10),
                hover = Color(255, 255, 255, 15),
                selected = Color(255, 255, 255, 22),
                success = Color(0xFF4FD98F),
                successSubtle = Color(79, 217, 143, 41),
                update = Color(0xFFFFA860),
                updateOn = Color(0xFF2A1400),
                updateInk = Color(0xFFFFA860),
                updateSubtle = Color(255, 168, 96, 46),
                warning = Color(0xFFF2C14E),
                warningSubtle = Color(242, 193, 78, 46),
                danger = Color(0xFFFF8E9A),
                dangerSubtle = Color(255, 117, 132, 46),
                busy = Color(0xFFBCB2FF),
                infoSubtle = Color(10, 132, 255, 56),
                track = Color(255, 255, 255, 46),
                thumbRing = Color(0xFF454A55),
                wallBase = Color(0xFF0B0F17),
                wallOrbs = listOf(Color(5, 60, 92, 242), Color(0, 106, 77, 191), Color(10, 47, 115, 242)),
                petalTop = Color(0xFF0A6FA8),
                petalBottom = Color(0xFF0A2F73),
                solid = Color(0xFF2B2E38),
            )

        // Fixed across appearances: the brand's and the terminal's own colours.
        val DangerFill = Color(0xFFC42B1C)
        val DangerFillHover = Color(0xFFB02719)
        val Deep = Color(0xFF0A3D36)
        val Mint = Color(0xFF3DDCB0)
        val Coral = Color(0xFFFF6B5B)
        val OnCoral = Color(0xFF2A0A05)
        val TerminalFill = Color(0xFF0A1214)
        val TerminalInk = Color(0xFFE8F4F1)
        val TerminalDim = Color(0xFF8FA8A3)
    }
}

/** The faces of the type ramp. The system's fonts are the consumer's to find; the defaults stand in when it finds none. */
@Immutable
data class FluentFonts(
    val text: FontFamily = FontFamily.Default,
    val display: FontFamily = FontFamily.Default,
    val mono: FontFamily = FontFamily.Monospace,
)

/** The type ramp. */
@Immutable
class FluentType(
    fonts: FluentFonts,
) {
    val hero = TextStyle(fontFamily = fonts.display, fontSize = 44.sp, lineHeight = 48.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-1.1).sp)
    val title = TextStyle(fontFamily = fonts.display, fontSize = 28.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.28).sp)
    val name = TextStyle(fontFamily = fonts.display, fontSize = 30.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.6).sp)
    val subtitle = TextStyle(fontFamily = fonts.display, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
    val strong = TextStyle(fontFamily = fonts.text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
    val body = TextStyle(fontFamily = fonts.text, fontSize = 14.sp, lineHeight = 21.sp)
    val small = TextStyle(fontFamily = fonts.text, fontSize = 13.sp, lineHeight = 19.sp)
    val caption = TextStyle(fontFamily = fonts.text, fontSize = 12.sp, lineHeight = 17.sp)
    val badge = TextStyle(fontFamily = fonts.text, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
    val mono = TextStyle(fontFamily = fonts.mono, fontSize = 12.sp, fontWeight = FontWeight.Medium)
    val code = TextStyle(fontFamily = fonts.mono, fontSize = 13.sp, fontWeight = FontWeight.Medium)
    val stat = TextStyle(fontFamily = fonts.display, fontSize = 30.sp, fontWeight = FontWeight.SemiBold)
}

val LocalFluent = staticCompositionLocalOf<FluentColors> { error("FluentTheme is not applied") }
val LocalFluentType = staticCompositionLocalOf<FluentType> { error("FluentTheme is not applied") }

/** [colors] and [fonts] for the components, and over the Material theme, whose menus and sliders they lean on. */
@Composable
fun FluentTheme(
    colors: FluentColors,
    fonts: FluentFonts = FluentFonts(),
    content: @Composable () -> Unit,
) {
    val type = remember(fonts) { FluentType(fonts) }
    val scheme =
        MaterialTheme.colorScheme.copy(
            primary = colors.accent,
            onPrimary = colors.accentOn,
            background = colors.solid,
            onBackground = colors.ink,
            surface = colors.solid,
            surfaceContainer = colors.solid,
            onSurface = colors.ink,
            onSurfaceVariant = colors.ink2,
        )
    val base = MaterialTheme.typography
    val typography =
        Typography(
            headlineSmall = base.headlineSmall.copy(fontFamily = fonts.display),
            titleLarge = base.titleLarge.copy(fontFamily = fonts.display),
            titleMedium = base.titleMedium.copy(fontFamily = fonts.text),
            titleSmall = base.titleSmall.copy(fontFamily = fonts.text),
            bodyLarge = base.bodyLarge.copy(fontFamily = fonts.text),
            bodyMedium = base.bodyMedium.copy(fontFamily = fonts.text),
            bodySmall = base.bodySmall.copy(fontFamily = fonts.text),
            labelLarge = base.labelLarge.copy(fontFamily = fonts.text),
        )
    CompositionLocalProvider(LocalFluent provides colors, LocalFluentType provides type) {
        MaterialTheme(colorScheme = scheme, typography = typography, shapes = MaterialTheme.shapes, content = content)
    }
}
