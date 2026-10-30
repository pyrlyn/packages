# kotlin-glass

https://github.com/pyrlyn/packages

Glass components for Compose Multiplatform, without business logic.

## Decisions

- Extracted from the ketch-kotlin desktop app's macOS design. What is generic lives here; the app keeps its screens,
  its data and the mapping of its design tokens to `GlassColors`.
- wasmJs is a target so that nothing platform-bound can creep into `commonMain`.
