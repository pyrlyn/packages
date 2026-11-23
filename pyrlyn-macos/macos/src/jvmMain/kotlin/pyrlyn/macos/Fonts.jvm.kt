package pyrlyn.macos

import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.platform.Font

private const val DIR = "pyrlyn/macos/fonts"

private val Inter =
    FontFamily(
        Font("$DIR/Inter-Regular.ttf", FontWeight.Normal),
        Font("$DIR/Inter-Medium.ttf", FontWeight.Medium),
        Font("$DIR/Inter-SemiBold.ttf", FontWeight.SemiBold),
        Font("$DIR/Inter-Bold.ttf", FontWeight.Bold),
    )

private val RobotoMono =
    FontFamily(
        Font("$DIR/RobotoMono-Regular.ttf", FontWeight.Normal),
        Font("$DIR/RobotoMono-Medium.ttf", FontWeight.Medium),
        Font("$DIR/RobotoMono-Bold.ttf", FontWeight.Bold),
    )

actual fun bundledFonts(platform: PlatformTextStyle?): MacFonts = MacFonts(Inter, RobotoMono, platform)
