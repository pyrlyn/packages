# AGENTS.md — kotlin-fluent

If an `AGENTS.md` or `CLAUDE.md` exists higher in the tree, follow it too; on conflict, ask the creator.

Fluent components for Compose Multiplatform in the Pyrlyn Windows 11 design system: a window material over a soft wall,
a title bar, a navigation view, cards with a lit edge, restrained 3D. Module `:fluent`, package `pyrlyn.fluent`;
consumers `includeBuild` this directory and kotlin-glass, and depend on `pyrlyn.fluent:fluent`.

## Rules

- UI only. No business logic, no consumer types, no consumer words: a component takes its words, colours and callbacks
  as parameters. The English defaults of `ToggleSwitch` and `CaptionButtons` are the design system's own.
- Components are `commonMain` only. A component that needs a platform API takes it as a callback (the clipboard is
  `onCopy`, the window's buttons are `onMinimize`, `onToggleMaximize` and `onClose`). `jvmMain` holds one thing, the
  native frame `WindowsFrame`, with JNA kept `internal`. It fails open: where it cannot attach, the window keeps working.
- A control is a real control: reachable by keyboard, with a focus ring, named for screen readers.
- Test tags and other per-use modifiers arrive through `modifier` parameters, never as a `tag` string.
- `Glyph` and `AutoGrid` belong to kotlin-glass. A new glyph goes there.
- Colours, sizes and type come from the design system's tokens. Where Compose cannot do what the CSS does, say so where
  the difference is (the flyout and the dialog are more opaque because nothing is blurred behind them).

Gate: `./gradlew check` (compile for jvm and wasmJs, the jvm tests, ktlint, detekt).
