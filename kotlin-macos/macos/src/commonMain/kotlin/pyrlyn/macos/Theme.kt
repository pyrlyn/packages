package pyrlyn.macos

import androidx.compose.foundation.text.selection.LocalTextSelectionColors
import androidx.compose.foundation.text.selection.TextSelectionColors
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** The MacOS-Pyrlyn-UI colour variables, one field per Figma variable. */
@Immutable
data class MacColors(
    val dark: Boolean,
    val bgWindow: Color,
    val bgChrome: Color,
    val bgEditor: Color,
    val bgElevated: Color,
    val bgFill: Color,
    val bgFillMedium: Color,
    val bgFillStrong: Color,
    val bgSelection: Color,
    val bgSwitchOff: Color,
    val bgTerminal: Color,
    val bgTerminalChrome: Color,
    val bgLineAdded: Color,
    val bgLineRemoved: Color,
    val borderHairline: Color,
    val borderSubtle: Color,
    val borderStrong: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textTertiary: Color,
    val textGhost: Color,
    val textOnAccent: Color,
    val textCode: Color,
    val textLineNumber: Color,
    val textFileIcon: Color,
    val textTerminal: Color,
    val textTerminalDim: Color,
    val accent: Color,
    val accentInk: Color,
    val statusBusy: Color,
    val statusOk: Color,
    val statusWarning: Color,
    val statusError: Color,
    val statusOkBg: Color,
    val statusBusyBg: Color,
    val statusWarningBg: Color,
    val statusErrorBg: Color,
    val bannerInfoBg: Color,
    val bannerInfoInk: Color,
    val bannerWarningBg: Color,
    val bannerWarningInk: Color,
    val bannerErrorBg: Color,
    val bannerErrorInk: Color,
    val windowClose: Color,
    val windowMinimize: Color,
    val windowZoom: Color,
    val scrim: Color,
    /** The Overlay elevation's two drop shadows and its lit top edge. */
    val shadowNear: Color,
    val shadowFar: Color,
    val highlight: Color,
) {
    companion object {
        /** The file's own values. */
        val Dark =
            MacColors(
                dark = true,
                bgWindow = Color(0xE6171A22),
                bgChrome = Color(0xFF1B1D22),
                bgEditor = Color(0xFF1F1F24),
                bgElevated = Color(0xF7262B37),
                bgFill = Color(0x14FFFFFF),
                bgFillMedium = Color(0x1FFFFFFF),
                bgFillStrong = Color(0x24FFFFFF),
                bgSelection = Color(0x3D0A84FF),
                bgSwitchOff = Color(0x2EFFFFFF),
                bgTerminal = Color(0xFF0A1214),
                bgTerminalChrome = Color(0xFF0F191C),
                bgLineAdded = Color(0x1F4FD98F),
                bgLineRemoved = Color(0x1FFF7584),
                borderHairline = Color(0x14FFFFFF),
                borderSubtle = Color(0x1FFFFFFF),
                borderStrong = Color(0x29FFFFFF),
                textPrimary = Color(0xFFEEF2F8),
                textSecondary = Color(0xFFA3ADBF),
                textTertiary = Color(0xFFC9D1DE),
                textGhost = Color(0xFF8A8F99),
                textOnAccent = Color.White,
                textCode = Color(0xFFDFDFE0),
                textLineNumber = Color(0xFF8A8A90),
                textFileIcon = Color(0xFFFFA860),
                textTerminal = Color(0xFFE8F4F1),
                textTerminalDim = Color(0xFF8FA3A8),
                accent = Color(0xFF0873E0),
                accentInk = Color(0xFF6CB6FF),
                statusBusy = Color(0xFFBCB2FF),
                statusOk = Color(0xFF4FD98F),
                statusWarning = Color(0xFFF2C14E),
                statusError = Color(0xFFFF8E9A),
                statusOkBg = Color(0x2E4FD98F),
                // The file has only the ok tint; the others take the same alpha.
                statusBusyBg = Color(0x2EBCB2FF),
                statusWarningBg = Color(0x2EF2C14E),
                statusErrorBg = Color(0x2EFF8E9A),
                bannerInfoBg = Color(0xFF0F2A44),
                bannerInfoInk = Color(0xFFCFE5FF),
                bannerWarningBg = Color(0xFF3A2F12),
                bannerWarningInk = Color(0xFFFFE6A3),
                // Derived the way info and warning are: the hue darkened for the fill, lightened for the ink.
                bannerErrorBg = Color(0xFF441A20),
                bannerErrorInk = Color(0xFFFFD1D6),
                windowClose = Color(0xFFFF5F57),
                windowMinimize = Color(0xFFFEBC2E),
                windowZoom = Color(0xFF28C840),
                scrim = Color(0x29000F2D),
                shadowNear = Color(0x73000000),
                shadowFar = Color(0x80000000),
                highlight = Color(0x38FFFFFF),
            )

        /** Derived for the light appearance: the file has dark screens only. */
        val Light =
            MacColors(
                dark = false,
                bgWindow = Color(0xEBF4F5F8),
                bgChrome = Color(0xFFECEEF2),
                bgEditor = Color(0xFFFFFFFF),
                bgElevated = Color(0xFAFFFFFF),
                bgFill = Color(0x0D000000),
                bgFillMedium = Color(0x14000000),
                bgFillStrong = Color(0x1A000000),
                bgSelection = Color(0x290A84FF),
                bgSwitchOff = Color(0x26000000),
                bgTerminal = Color(0xFFF3F6F6),
                bgTerminalChrome = Color(0xFFE7EDED),
                bgLineAdded = Color(0x261F9D5A),
                bgLineRemoved = Color(0x1FD7334A),
                borderHairline = Color(0x14000000),
                borderSubtle = Color(0x1F000000),
                borderStrong = Color(0x2E000000),
                textPrimary = Color(0xFF1D1F24),
                textSecondary = Color(0xFF5B6475),
                textTertiary = Color(0xFF3F4756),
                textGhost = Color(0xFF8C929C),
                textOnAccent = Color.White,
                textCode = Color(0xFF2B2D33),
                textLineNumber = Color(0xFF8E8E94),
                textFileIcon = Color(0xFFC2620E),
                textTerminal = Color(0xFF1D2B2E),
                textTerminalDim = Color(0xFF5D6F73),
                accent = Color(0xFF0873E0),
                accentInk = Color(0xFF0A62BF),
                statusBusy = Color(0xFF6E5BD6),
                statusOk = Color(0xFF1C8A50),
                statusWarning = Color(0xFF946600),
                statusError = Color(0xFFC72E3E),
                statusOkBg = Color(0x241C8A50),
                statusBusyBg = Color(0x246E5BD6),
                statusWarningBg = Color(0x24946600),
                statusErrorBg = Color(0x24C72E3E),
                bannerInfoBg = Color(0xFFE3EFFD),
                bannerInfoInk = Color(0xFF0B3D73),
                bannerWarningBg = Color(0xFFFFF3D4),
                bannerWarningInk = Color(0xFF5C4300),
                bannerErrorBg = Color(0xFFFDE6E9),
                bannerErrorInk = Color(0xFF7A1422),
                windowClose = Color(0xFFFF5F57),
                windowMinimize = Color(0xFFFEBC2E),
                windowZoom = Color(0xFF28C840),
                scrim = Color(0x1F000F2D),
                shadowNear = Color(0x26000000),
                shadowFar = Color(0x33000000),
                highlight = Color(0x99FFFFFF),
            )
    }
}

/** The faces the type uses. [platform] carries the consumer's text rasterisation settings. */
@Immutable
data class MacFonts(
    val text: FontFamily = FontFamily.Default,
    val mono: FontFamily = FontFamily.Monospace,
    val platform: PlatformTextStyle? = null,
)

/** The library's text styles, by their Figma names. */
@Immutable
class MacType(
    fonts: MacFonts,
) {
    private val sans = TextStyle(fontFamily = fonts.text, platformStyle = fonts.platform)
    private val fixed = TextStyle(fontFamily = fonts.mono, platformStyle = fonts.platform)

    val title = sans.with(17.sp, 22.sp, FontWeight.SemiBold, (-0.1).sp)
    val body = sans.with(13.sp, 18.sp, FontWeight.Normal)
    val bodyEmphasized = sans.with(13.sp, 18.sp, FontWeight.SemiBold)
    val small = sans.with(12.sp, 16.sp, FontWeight.Normal)
    val smallEmphasized = sans.with(12.sp, 16.sp, FontWeight.SemiBold)
    val caption = sans.with(11.sp, 14.sp, FontWeight.Normal)
    val captionBold = sans.with(11.sp, 14.sp, FontWeight.Bold)

    /** Upper-case by the caller: Compose has no text-transform. */
    val sectionLabel = sans.with(11.sp, 14.sp, FontWeight.SemiBold, 0.6.sp)

    /** "Text/Body 14 Regular", the text field's value. */
    val field = sans.with(14.sp, 16.8.sp, FontWeight.Normal)
    val code = fixed.with(12.sp, 18.sp, FontWeight.Normal)
    val codeSmall = fixed.with(10.sp, 12.sp, FontWeight.Normal)
    val terminal = fixed.with(12.sp, 17.sp, FontWeight.Normal)
}

private fun TextStyle.with(
    size: TextUnit,
    line: TextUnit,
    weight: FontWeight,
    tracking: TextUnit = 0.sp,
) = copy(fontSize = size, lineHeight = line, fontWeight = weight, letterSpacing = tracking)

/** The file's `space` variables. */
object MacSpace {
    val xxs = 2.dp
    val xs = 4.dp
    val sm = 6.dp
    val md = 8.dp
    val lg = 10.dp
    val xl = 12.dp
    val xxl = 14.dp
    val xxxl = 16.dp
    val xxxxl = 28.dp
}

/** The file's `radius` variables; `radius/full` is a [androidx.compose.foundation.shape.CircleShape]. */
object MacRadius {
    val sm = 6.dp
    val md = 8.dp

    /** The text field's own corner, which the file sets outside the scale. */
    val field = 12.dp
    val lg = 14.dp
    val xl = 16.dp

    /** The composer card's corner, outside the file's scale, from the composer references. */
    val card = 20.dp
    val window = 26.dp
}

/** Fixed sizes of the library's parts. */
object MacSize {
    val icon = 16.dp
    val buttonRegular = 24.dp
    val buttonLarge = 28.dp
    val iconButton = 28.dp
    val sendButton = 28.dp
    val picker = 28.dp
    val iconTile = 28.dp
    val popUp = 32.dp
    val field = 44.dp
    val searchField = 28.dp
    val toolbar = 44.dp
    val chip = 18.dp
    val statusDot = 8.dp
    val settingsItem = 30.dp
    val sidebar = 280.dp
    val sheet = 560.dp
    val switchWidth = 32.dp
    val switchHeight = 18.dp
    val hairline: Dp = 1.dp
}

/**
 * The palette for a window over the system's behind-window material (an NSVisualEffectView): the sidebar and toolbar
 * let the blurred desktop through, while the editor ground mostly covers it so text keeps its contrast.
 * Use the plain palette when the user asks for less transparency.
 */
fun MacColors.vibrant(): MacColors = copy(bgChrome = bgChrome.copy(alpha = VIBRANT_CHROME), bgEditor = bgEditor.copy(alpha = VIBRANT_EDITOR))

private const val VIBRANT_CHROME = 0.55f
private const val VIBRANT_EDITOR = 0.86f

private val LocalMacColors = staticCompositionLocalOf { MacColors.Dark }
private val LocalMacType = staticCompositionLocalOf { MacType(MacFonts()) }

object Mac {
    val colors: MacColors
        @Composable get() = LocalMacColors.current
    val type: MacType
        @Composable get() = LocalMacType.current
}

/** MacOS-Pyrlyn-UI for [content]: every component reads [Mac]. */
@Composable
fun MacTheme(
    colors: MacColors = MacColors.Dark,
    fonts: MacFonts = MacFonts(),
    content: @Composable () -> Unit,
) {
    val type = remember(fonts) { MacType(fonts) }
    val selection = remember(colors) { TextSelectionColors(colors.accent, colors.bgSelection) }
    CompositionLocalProvider(
        LocalMacColors provides colors,
        LocalMacType provides type,
        LocalTextSelectionColors provides selection,
        content = content,
    )
}

/** How a chip, status dot or banner reads. */
enum class Tone { Busy, Ok, Warning, Error, Idle, Info }

/** The ink of [tone]: the dot, the chip's word. */
@Composable
fun Tone.ink(): Color =
    Mac.colors.let {
        when (this) {
            Tone.Busy -> it.statusBusy
            Tone.Ok -> it.statusOk
            Tone.Warning -> it.statusWarning
            Tone.Error -> it.statusError
            Tone.Idle -> it.textTertiary
            Tone.Info -> it.accentInk
        }
    }

/** The capsule fill behind a chip of [tone]. */
@Composable
fun Tone.tint(): Color =
    Mac.colors.let {
        when (this) {
            Tone.Busy -> it.statusBusyBg
            Tone.Ok -> it.statusOkBg
            Tone.Warning -> it.statusWarningBg
            Tone.Error -> it.statusErrorBg
            Tone.Idle -> it.bgFillMedium
            Tone.Info -> it.bgSelection
        }
    }
