# kotlin-webgl

Reusable WebGL2 rendering for Kotlin/Wasm (Compose Multiplatform web).

| Module | What |
|---|---|
| `pyrlyn.webgl:webgl` | Thin typed WebGL2 wrapper: `Gl.of(canvas)`, `begin`, `program(vs, fs)` → `Program.set(uniform)`, `buffer()` → `FloatBuffer.upload`, `vertexArray().attribute`, `draw(program, vao, Mode, count)`. |
| `pyrlyn.webgl:webgl-compose` | `@Composable WebGlCanvas(scene: GlScene)`: hosts a canvas via `HtmlElementView`, DPR-aware resize, rAF loop while `animating()`, pointer/wheel input as `CanvasInput`. |
| `pyrlyn.webgl:graph` | Generic graph cloud: model (`Cloud`/`Graph`/`Node`/`Edge`), `forceLayout`, `layoutCloud` (clusters packed side by side), `Camera` (pan, zoom about cursor, `fit`, eased `flyTo`), LOD (`detailOf`: summary disc vs nodes+edges), `pick`. Common code, JVM-tested. wasmJs: `CloudState` (input, focus, back) + `CloudRenderer` (points/lines shaders). |

Use from another build: `includeBuild(System.getenv("KOTLIN_WEBGL_DIR") ?: "../../packages/kotlin-webgl")`.

Check: `CI_DIFFUSER_DIR=… ./gradlew check`.
