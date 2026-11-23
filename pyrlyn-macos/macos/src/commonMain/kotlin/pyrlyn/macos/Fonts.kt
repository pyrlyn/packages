package pyrlyn.macos

import androidx.compose.ui.text.PlatformTextStyle

/**
 * Inter and Roboto Mono (both SIL OFL 1.1), the faces the design uses, with the consumer's [platform] settings.
 * On a platform that cannot load them synchronously the families fall back to the default ones.
 */
expect fun bundledFonts(platform: PlatformTextStyle? = null): MacFonts
