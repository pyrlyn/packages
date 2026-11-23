package pyrlyn.macos

import androidx.compose.ui.text.PlatformTextStyle

// A browser loads font bytes asynchronously; a wasm consumer passes the families it loaded to MacTheme instead.
actual fun bundledFonts(platform: PlatformTextStyle?): MacFonts = MacFonts(platform = platform)
