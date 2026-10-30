# kotlin-glass

Glass components for Compose Multiplatform, for jvm and wasmJs: panes over a soft wallpaper with a lit edge, buttons,
a segmented control, a search field, badges, a modal sheet, a floating status bar, list rows, tiles, cards, a hero
panel, and a sidebar and toolbar to hold them. No business logic: every word, colour and action comes from the caller.

```kotlin
// settings.gradle.kts
includeBuild("../../packages/kotlin-glass")

// build.gradle.kts
implementation("pyrlyn.glass:glass")
```

```kotlin
GlassTheme(colors) {           // colors: GlassColors, mapped from your own tokens
    Box {
        Wallpaper()
        Column(Modifier.glass(Pane.Card).padding(20.dp)) {
            SectionTitle("Hello")
            GlassButton("Go", ButtonKind.Primary) { go() }
        }
    }
}
```

| File | Holds |
| --- | --- |
| `Colors.kt` | `GlassColors`, `Pane`, `GlassTheme` |
| `Surfaces.kt` | `Wallpaper`, `Modifier.glass`, `Modifier.tilt`, `LetterIcon`, `ColorTile`, text styles |
| `Glyph.kt` | `Glyph`, `GlyphIcon` |
| `Controls.kt` | Buttons, `Segmented`, `SearchField`, `Badge`, `Tag`, `CommandField`, `GlassSwitch`, `GlassSlider`, `Select`, `Swatch` |
| `Panels.kt` | `Sheet`, `StatusBar`, `ListRow`, `Tile`, `FeatureCard`, `StatCard`, `Facts`, `HeroPanel` |
| `Layout.kt` | `SidebarItem`, `Toolbar`, `NavigationArrows`, `AutoGrid` |

Compose has no live backdrop blur, so the glass is translucent panes over a still, blurred wallpaper, and what floats over
content (a sheet, the status bar) stays nearly opaque to keep it legible.
