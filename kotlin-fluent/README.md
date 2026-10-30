# kotlin-fluent

Fluent components for Compose Multiplatform, for jvm and wasmJs, in the Pyrlyn Windows 11 design system: a wall under a
window material, a title bar with caption buttons, a navigation view, a content layer, cards with a lit edge, extruded
icons, the design system's controls, an activity flyout and a content dialog. No business logic: every word, colour and
action comes from the caller.

```kotlin
// settings.gradle.kts
includeBuild("../../packages/kotlin-glass")
includeBuild("../../packages/kotlin-fluent")

// build.gradle.kts
implementation("pyrlyn.fluent:fluent")
```

```kotlin
FluentTheme(FluentColors.Light) {
    Box {
        Wall()
        WindowMaterialLayer()
        ContentLayer {
            Panel {
                SectionHead("Hello")
                FluentButton("Go", ButtonStyle.Accent) { go() }
            }
        }
    }
}
```

| File | Holds |
| --- | --- |
| `Colors.kt` | `FluentColors`, `WindowMaterial`, `Presets`, `FluentType`, `FluentTheme` |
| `Surfaces.kt` | `Wall`, `WindowMaterialLayer`, `Modifier.card`, `Modifier.lift`, `AppIcon`, `FeatureIcon`, `BrandTile` |
| `Controls.kt` | Buttons, `SearchBox`, `ToggleSwitch`, `ComboBox`, `FluentSlider`, `RadioButtons`, `Swatch`, `SelectorBar`, badges, `ReleaseTag`, `CommandField`, `TerminalOutput`, progress |
| `Layout.kt` | `TitleBar`, `CaptionButtons`, `NavItem`, `ContentLayer`, `PageHead`, `Breadcrumb` |
| `Panels.kt` | `Panel`, `LiftPanel`, `ListRow`, `SettingsCard`, `Tile`, `FeatureCard`, `StatCard`, `ActivityFlyout`, `InfoBar`, `FactsList`, `LogRow`, `HeroPanel`, `HeroScene` |
| `Dialog.kt` | `ContentDialog` |
| `WindowZones.kt` | `WindowZone`, `WindowZones`, `Modifier.windowZone`: which parts of a self-drawn title bar move the window |
| `WindowsFrame.kt` (jvm) | `WindowsFrame`: the system's frame on Windows 11 for an undecorated window |

Compose has no live backdrop blur, so the window draws its own wall and the materials are translucent fills over it.
On Windows 11 `WindowsFrame` gives an undecorated `ComposeWindow` the system's rounded corners, shadow, Snap Layouts and
system menu, and puts Mica or Acrylic behind a see-through canvas. `LocalWindowBackdrop` then drops the wall and the fill.
Attach it before the window is first shown; it returns null off Windows 11, and the window goes on as it was.
`Glyph`, `GlyphIcon` and `AutoGrid` come from kotlin-glass.
