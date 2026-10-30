# AGENTS.md — kotlin-adwaita

If an `AGENTS.md` or `CLAUDE.md` exists higher in the tree, follow it too; on conflict, ask the creator.

Adwaita-style components for Compose Multiplatform in the Pyrlyn Linux design system: a dark translucent window over a
soft wall, a header bar with a view switcher, boxed lists, pill buttons, status pills, popovers and toasts. Module
`:adwaita`, package `pyrlyn.adwaita`; consumers `includeBuild` this directory and kotlin-glass, and depend on
`pyrlyn.adwaita:adwaita`.

## Rules

- UI only. No business logic, no consumer types, no consumer words: a component takes its words, colours and callbacks
  as parameters.
- Components are `commonMain` only. A component that needs a platform API takes it as a callback (the window's buttons
  are `onMinimize`, `onToggleMaximize` and `onClose`, the clipboard is `onCopy`).
- A control is a real control: reachable by keyboard, with a focus ring, named for screen readers.
- Test tags and other per-use modifiers arrive through `modifier` parameters, never as a `tag` string.
- `Glyph` and `AutoGrid` belong to kotlin-glass. A new glyph goes there.
- Colours, sizes and type come from the design system's tokens. Where Compose cannot do what the CSS does, say so where
  the difference is (popovers and dialogs are more opaque because nothing is blurred behind them).

Gate: `./gradlew check` (compile for jvm and wasmJs, the jvm tests, ktlint, detekt).
