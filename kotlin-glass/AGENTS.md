# AGENTS.md — kotlin-glass

If an `AGENTS.md` or `CLAUDE.md` exists higher in the tree, follow it too; on conflict, ask the creator.

Glass components for Compose Multiplatform: panes over a soft wallpaper, buttons, fields, sheets, cards.
Module `:glass`, package `pyrlyn.glass`; consumers `includeBuild` this directory and depend on
`pyrlyn.glass:glass`.

## Rules

- UI only. No business logic, no consumer types, no consumer words: a component takes its words, colours and
  callbacks as parameters. A consumer maps its own tokens to `GlassColors` and owns that mapping and its tests.
- `commonMain` only. A component that needs a platform API takes it as a callback (the clipboard is `onCopy`).
- A control is a real control: reachable by keyboard, with a focus ring, named for screen readers.
- Test tags and other per-use modifiers arrive through `modifier` parameters, never as a `tag` string.

Gate: `./gradlew check` (compile for jvm and wasmJs, the jvm tests, ktlint, detekt).
