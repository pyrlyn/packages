// The colours are the design system's own values; a name for each would only repeat it.
@file:Suppress("MagicNumber")

// The Ketch Linux palette and type ramp: a dark glass window on a violet and teal wall, drawn with GNOME's shapes.
// The design system has one appearance; a consumer that needs light or high contrast uses another look.

package pyrlyn.adwaita

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp

/** The design system's colours. A consumer may change the accent; nothing else depends on the app. */
@Immutable
data class AdwaitaColors(
    val ink: Color = Color(0xFFF1F4FA),
    val ink2: Color = Color(236, 241, 250, 189),
    val line: Color = Color(255, 255, 255, 33),
    val glass: Color = Color(24, 27, 40, 117),
    val fill1: Color = Color(255, 255, 255, 18),
    val fill2: Color = Color(255, 255, 255, 31),
    val fill3: Color = Color(255, 255, 255, 46),
    val hover: Color = Color(255, 255, 255, 26),
    val selected: Color = Color(255, 255, 255, 36),
    val accent: Color = Color(0xFF3DDCB0),
    val accentHover: Color = Color(0xFF5EE6C0),
    val accentInk: Color = Color(0xFF06261F),
    val accentText: Color = Color(0xFF8FF0D2),
    val linkHover: Color = Color(0xFFC9FFF0),
    val update: Color = Color(0xFFFF8A7A),
    val updateHover: Color = Color(0xFFFFA194),
    val updateInk: Color = Color(0xFF3A0D07),
    val danger: Color = Color(0xFFC0392B),
    val dangerHover: Color = Color(0xFFD24435),
    val dangerText: Color = Color(0xFFFF9A92),
    val warning: Color = Color(0xFFFFC06B),
    val warningInk: Color = Color(0xFF3A2400),
    val info: Color = Color(0xFF6FB1FF),
    val scrim: Color = Color(6, 8, 16, 115),
    // Nothing is blurred behind an overlay here, so the design's glass-strong (.72) and toast (.9) would let the text
    // under them through; they stand a little thicker.
    val overlay: Color = Color(30, 33, 50, 247),
    val overlayLine: Color = Color(255, 255, 255, 41),
    val toast: Color = Color(36, 40, 58, 247),
    val terminal: Color = Color(0, 0, 0, 102),
    val terminalInk: Color = Color(225, 235, 245, 230),
    val well: Color = Color(0, 0, 0, 71),
)

/** The faces of the type ramp. Inter and JetBrains Mono are the consumer's to find; the defaults stand in without them. */
@Immutable
data class AdwaitaFonts(
    val text: FontFamily = FontFamily.Default,
    val mono: FontFamily = FontFamily.Monospace,
)

/** The type ramp. */
@Immutable
class AdwaitaType(
    fonts: AdwaitaFonts,
) {
    val title1 = TextStyle(fontFamily = fonts.text, fontSize = 36.sp, lineHeight = 40.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.02).em)
    val page = TextStyle(fontFamily = fonts.text, fontSize = 30.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.02).em)
    val title2 = TextStyle(fontFamily = fonts.text, fontSize = 28.sp, fontWeight = FontWeight.Bold)
    val title3 = TextStyle(fontFamily = fonts.text, fontSize = 20.sp, fontWeight = FontWeight.Bold)
    val section = TextStyle(fontFamily = fonts.text, fontSize = 17.sp, fontWeight = FontWeight.Bold)
    val heading = TextStyle(fontFamily = fonts.text, fontSize = 15.sp, fontWeight = FontWeight.Bold)
    val lead = TextStyle(fontFamily = fonts.text, fontSize = 15.sp, lineHeight = 23.sp)
    val body = TextStyle(fontFamily = fonts.text, fontSize = 14.sp, lineHeight = 21.sp)
    val bodyStrong = TextStyle(fontFamily = fonts.text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
    val caption = TextStyle(fontFamily = fonts.text, fontSize = 13.sp, lineHeight = 18.sp)
    val captionStrong = TextStyle(fontFamily = fonts.text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
    val small = TextStyle(fontFamily = fonts.text, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
    val eyebrow = TextStyle(fontFamily = fonts.text, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.08.em)
    val badge = TextStyle(fontFamily = fonts.text, fontSize = 11.sp, fontWeight = FontWeight.Bold)
    val mono = TextStyle(fontFamily = fonts.mono, fontSize = 13.sp, fontWeight = FontWeight.Medium)
    val monoSmall = TextStyle(fontFamily = fonts.mono, fontSize = 12.sp)
    val terminal = TextStyle(fontFamily = fonts.mono, fontSize = 13.sp, lineHeight = 23.sp, fontWeight = FontWeight.Medium)
}

val LocalAdwaita = staticCompositionLocalOf<AdwaitaColors> { error("AdwaitaTheme is not applied") }
val LocalAdwaitaType = staticCompositionLocalOf<AdwaitaType> { error("AdwaitaTheme is not applied") }

/** [colors] and [fonts] for the components, and over the Material theme, whose text selection and menus they lean on. */
@Composable
fun AdwaitaTheme(
    colors: AdwaitaColors = AdwaitaColors(),
    fonts: AdwaitaFonts = AdwaitaFonts(),
    content: @Composable () -> Unit,
) {
    val type = remember(fonts) { AdwaitaType(fonts) }
    val scheme =
        MaterialTheme.colorScheme.copy(
            primary = colors.accent,
            onPrimary = colors.accentInk,
            background = colors.overlay,
            onBackground = colors.ink,
            surface = colors.overlay,
            surfaceContainer = colors.overlay,
            onSurface = colors.ink,
            onSurfaceVariant = colors.ink2,
        )
    val base = MaterialTheme.typography
    val typography =
        Typography(
            titleMedium = base.titleMedium.copy(fontFamily = fonts.text),
            titleSmall = base.titleSmall.copy(fontFamily = fonts.text),
            bodyLarge = base.bodyLarge.copy(fontFamily = fonts.text),
            bodyMedium = base.bodyMedium.copy(fontFamily = fonts.text),
            bodySmall = base.bodySmall.copy(fontFamily = fonts.text),
            labelLarge = base.labelLarge.copy(fontFamily = fonts.text),
        )
    CompositionLocalProvider(LocalAdwaita provides colors, LocalAdwaitaType provides type) {
        MaterialTheme(colorScheme = scheme, typography = typography, shapes = MaterialTheme.shapes, content = content)
    }
}
