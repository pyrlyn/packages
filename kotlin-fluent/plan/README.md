# kotlin-fluent

https://github.com/pyrlyn/packages

Fluent components for Compose Multiplatform in the Pyrlyn Windows 11 design system, without business logic.

## Decisions

- Built for the ketch-kotlin desktop app's Windows look. What is generic lives here; the app keeps its screens, its data
  and the mapping of its settings to `FluentColors`.
- compose-fluent-ui is not used: its last release, v0.1.0 of August 2025, is on Compose 1.8.2 and its controls carry
  Microsoft's stock Fluent tokens, while this design system has its own surfaces, 3D and palette.
- kotlin-glass stays a dependency for `Glyph`, `GlyphIcon`, `AutoGrid` and `keyboardFocus`, so the two looks share them.
- wasmJs is a target so that nothing platform-bound can creep into `commonMain`.
