# kotlin-adwaita

Adwaita-style components for Compose Multiplatform, for jvm and wasmJs, in the Pyrlyn Linux design system: a wall under
a dark translucent window, a header bar with a view switcher, boxed lists and preferences groups, pill buttons, status
pills, popovers, toasts and alert dialogs. No business logic: every word, colour and action comes from the caller.

```kotlin
// settings.gradle.kts
includeBuild("../../packages/kotlin-glass")
includeBuild("../../packages/kotlin-adwaita")

// build.gradle.kts
implementation("pyrlyn.adwaita:adwaita")
```

```kotlin
AdwaitaTheme {
    Box {
        Wall()
        WindowGlass()
        Clamp {
            PageHead("Hello")
            BoxedList { ActionRow(trailing = { PillButton("Go", style = PillStyle.Accent) { go() } }) { RowTitle("One") } }
        }
    }
}
```

| File | Holds |
| --- | --- |
| `Colors.kt` | `AdwaitaColors`, `AdwaitaFonts`, `AdwaitaType`, `AdwaitaTheme` |
| `Surfaces.kt` | `Wall`, `WindowGlass`, `Corners`, `Modifier.boxed`, `IconGradient`, `IconSphere`, `AppAvatar` |
| `Controls.kt` | `PillButton`, `FlatButton`, `ViewSwitcher`, `Entry`, `ToggleSwitch`, `StatusPill`, `DotBadge`, `GlowDot`, `TextLink`, `Terminal`, `CommandLine`, `Facts`, `ProgressBar`, `Spinner` |
| `Layout.kt` | `HeaderBar`, `WindowControls`, `Clamp`, `PageHead`, `Breadcrumb`, `PreferencesGroup`, `BoxedList`, `ActionRow`, `RowTitle`, `Tile` |
| `Overlays.kt` | `PopoverMenu`, `MenuItem`, `MenuLabel`, `MenuSeparator`, `Toast`, `Banner`, `AlertDialog` |
| `Panels.kt` | `TintedPanel`, `HeroScene`, `Eyebrow`, `EmptyNote`, `RadioRow`, `Select` |

Compose has no live backdrop blur, so the window draws its own wall and the materials are translucent fills over it.
The design system is dark only. `Glyph`, `GlyphIcon` and `AutoGrid` come from kotlin-glass.
