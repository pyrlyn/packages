# kotlin-macos

https://github.com/pyrlyn/packages

macOS-style components for Compose Multiplatform from the MacOS-Pyrlyn-UI design, without business logic.

## Decisions

- Built for Octo's macOS look. What is generic lives here; Octo keeps its screens, data and the mapping from its own
  design layer.
- No Compose library draws the macOS look on jvm and wasmJs: compose-cupertino, the nearest kind of project, is iOS
  only, and the Swing Aqua look-and-feel does not reach Compose.
- Inter and Roboto Mono are bundled under the SIL Open Font License, so the look does not depend on fonts installed on
  the machine.
- The Figma file has dark screens only; the light palette is derived from the same hues.
- The composer and its pickers follow the creator's reference screenshots, which the file does not cover: the card
  radius (`MacRadius.card`) and the round send button come from them.
- wasmJs is a target so that nothing platform-bound can creep into `commonMain`.
