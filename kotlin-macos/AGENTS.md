# AGENTS.md — kotlin-macos

If an `AGENTS.md` or `CLAUDE.md` exists higher in the tree, follow it too; on conflict, ask the creator.

macOS-style components for Compose Multiplatform from the MacOS-Pyrlyn-UI Figma file: tokens, controls, fields,
lists, menus, sheets, a transcript and a composer. Module `:macos`, package `pyrlyn.macos`; consumers `includeBuild`
this directory and depend on `pyrlyn.macos:macos`.

## Rules

- UI only. No business logic, no consumer types, no consumer words: a component takes its words, colours and callbacks
  as parameters.
- Components are `commonMain` only; the bundled fonts are the one per-platform part.
- A control is a real control: reachable by keyboard, with a focus ring, named for screen readers.
- Colours, sizes and type come from the tokens in `Theme.kt`, which follow the Figma file's variables. A value the file
  does not define says where it comes from.
- Icons are the file's 16-point stroke paths in `Icons.kt`; a new one is drawn the same way.

Gate: `./gradlew check` (compile for jvm and wasmJs, the jvm tests, ktlint, detekt).
