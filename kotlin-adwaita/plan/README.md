# kotlin-adwaita

https://github.com/pyrlyn/packages

Adwaita-style components for Compose Multiplatform in the Pyrlyn Linux design system, without business logic.

## Decisions

- Built for the ketch-kotlin desktop app's Linux look. What is generic lives here; the app keeps its screens and data.
- No ready Compose Adwaita library exists, and a port of libadwaita would carry GNOME's stock tokens, while this design
  system has its own glass, wall and palette. compose-cupertino, the nearest kind of project, is iOS only.
- kotlin-glass stays a dependency for `Glyph`, `GlyphIcon`, `AutoGrid` and `keyboardFocus`, so the looks share them.
- Dark only, as the design system is.
- wasmJs is a target so that nothing platform-bound can creep into `commonMain`.
