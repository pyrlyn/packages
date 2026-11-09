# kotlin-macos

macOS-style components for Compose Multiplatform, for jvm and wasmJs, from the MacOS-Pyrlyn-UI design: a sidebar and
toolbar, pill buttons, pickers, pop-up buttons, fields, menus, sheets, a chat transcript and a composer. Dark and light
palettes, Inter and Roboto Mono bundled. No business logic: every word, colour and action comes from the caller.

```kotlin
// settings.gradle.kts
includeBuild("../../packages/kotlin-macos")

// build.gradle.kts
implementation("pyrlyn.macos:macos")
```

```kotlin
MacTheme(MacColors.Dark) {
    Composer(draft, { draft = it }, onSend = ::send, placeholder = "Message…") {
        IconButton(MacIcon.Plus, "Attach files", ::attach)
        PickerButton("Full access", ::openModes, icon = MacIcon.Shield, tint = Mac.colors.statusWarning)
    }
}
```

| File | Holds |
| --- | --- |
| `Theme.kt` | `MacColors`, `MacFonts`, `MacType`, `MacSpace`, `MacRadius`, `MacSize`, `Mac`, `MacTheme`, `Tone` |
| `Content.kt` | `Label`, `ProvideMacContent`, `controlState`, `focusRing` |
| `MacIcon.kt` | `MacIcon`, `Icon` |
| `Controls.kt` | `Button`, `IconButton`, `PickerButton`, `SendButton`, `Chip`, `Switch`, `PopUpButton`, `Field`, `ControlRow` |
| `Fields.kt` | `TextField`, `TextArea`, `SearchField`, `SearchButton` |
| `Lists.kt` | `SectionLabel`, `SidebarRow`, `SettingsItem`, `Status`, `Toolbar`, `ListRow`, hairline modifiers |
| `Surfaces.kt` | elevation modifiers, `Scrim`, `Sheet`, `SheetHeading`, `SheetButtons`, `Rule`, `IconTile`, `StatusDot`, `WindowControls` |
| `Overlays.kt` | `Menu`, `MenuItem`, `MenuSection`, `MenuSeparator`, `HoverCard`, `SplitButton` |
| `Transcript.kt` | `Banner`, `Message`, `CommandCard`, `LabeledDivider`, `Disclosure`, `Composer` |

The design file draws dark screens only; the light palette is derived from it. Compose has no vibrancy, so the
sidebar and sheets are opaque fills.
