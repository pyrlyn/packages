// A Composable that hosts a WebGL2 <canvas> (Compose's HtmlElementView), keeps its pixel size in step with the
// layout and device pixel ratio, and forwards pointer and wheel input in CSS pixels.
@file:OptIn(ExperimentalWasmJsInterop::class, ExperimentalComposeUiApi::class)
@file:Suppress("FunctionNaming")

package pyrlyn.webgl.compose

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.HtmlElementView
import kotlinx.browser.document
import kotlinx.browser.window
import org.w3c.dom.HTMLCanvasElement
import org.w3c.dom.events.MouseEvent
import org.w3c.dom.events.WheelEvent
import pyrlyn.webgl.Gl

/** Pointer input on the canvas, in CSS pixels from its top-left corner. */
sealed interface CanvasInput {
    val x: Double
    val y: Double

    data class Down(
        override val x: Double,
        override val y: Double,
    ) : CanvasInput

    data class Move(
        override val x: Double,
        override val y: Double,
        val dragging: Boolean,
    ) : CanvasInput

    data class Up(
        override val x: Double,
        override val y: Double,
    ) : CanvasInput

    /** A wheel step; [delta] > 0 scrolls away (zoom out). */
    data class Wheel(
        override val x: Double,
        override val y: Double,
        val delta: Double,
    ) : CanvasInput

    data class DoubleClick(
        override val x: Double,
        override val y: Double,
    ) : CanvasInput
}

/** What the host hands to the renderer each frame. */
interface GlScene {
    /** Called once with the context; return false to show [fallback] instead (no WebGL2). */
    fun init(gl: Gl): Boolean

    /** Draws a frame; [width]/[height] are device pixels, [cssWidth]/[cssHeight] CSS pixels, [dpr] their ratio. */
    fun frame(
        gl: Gl,
        width: Int,
        height: Int,
        dpr: Double,
        timeMs: Double,
    )

    /** Pointer input; return true when the scene changed and wants frames. */
    fun input(e: CanvasInput): Boolean = false

    /** Whether the scene still animates (camera flight); the host keeps requesting frames while true. */
    fun animating(): Boolean = false

    fun dispose() = Unit
}

/** Hosts [scene] in a WebGL2 canvas sized by [modifier]. */
@Composable
fun WebGlCanvas(
    scene: GlScene,
    modifier: Modifier = Modifier,
    label: String = "WebGL scene",
) {
    val host = remember(scene) { Host(scene) }
    DisposableEffect(host) { onDispose { host.dispose() } }
    HtmlElementView(
        factory = {
            (document.createElement("canvas") as HTMLCanvasElement).also {
                it.setAttribute("role", "img")
                it.setAttribute("aria-label", label)
                it.style.width = "100%"
                it.style.height = "100%"
                it.style.setProperty("touch-action", "none")
                host.attach(it)
            }
        },
        modifier = modifier,
        update = { host.invalidate() },
    )
}

/** Owns the canvas, the context and the frame loop. */
private class Host(
    private val scene: GlScene,
) {
    private var canvas: HTMLCanvasElement? = null
    private var gl: Gl? = null
    private var pending = false
    private var down = false
    private var disposed = false

    fun attach(c: HTMLCanvasElement) {
        canvas = c
        gl = Gl.of(c)?.takeIf { scene.init(it) }
        c.addEventListener("pointerdown", { e ->
            (e as? MouseEvent)?.let {
                down = true
                feed(CanvasInput.Down(it.offsetX, it.offsetY))
            }
        })
        c.addEventListener("pointermove", { e -> (e as? MouseEvent)?.let { feed(CanvasInput.Move(it.offsetX, it.offsetY, down)) } })
        c.addEventListener("pointerup", { e ->
            (e as? MouseEvent)?.let {
                down = false
                feed(CanvasInput.Up(it.offsetX, it.offsetY))
            }
        })
        c.addEventListener("pointerleave", { down = false })
        c.addEventListener("dblclick", { e -> (e as? MouseEvent)?.let { feed(CanvasInput.DoubleClick(it.offsetX, it.offsetY)) } })
        c.addEventListener("wheel", { e ->
            (e as? WheelEvent)?.let {
                it.preventDefault()
                feed(CanvasInput.Wheel(it.offsetX, it.offsetY, it.deltaY))
            }
        })
        window.addEventListener("resize", { invalidate() })
        invalidate()
    }

    private fun feed(e: CanvasInput) {
        if (scene.input(e)) invalidate()
    }

    /** Asks for one frame (coalesced). */
    fun invalidate() {
        if (pending || disposed) return
        pending = true
        window.requestAnimationFrame { t -> frame(t) }
    }

    private fun frame(t: Double) {
        pending = false
        val c = canvas ?: return
        val g = gl ?: return
        val dpr = window.devicePixelRatio
        val w = (c.clientWidth * dpr).toInt().coerceAtLeast(1)
        val h = (c.clientHeight * dpr).toInt().coerceAtLeast(1)
        if (c.width != w) c.width = w
        if (c.height != h) c.height = h
        scene.frame(g, w, h, dpr, t)
        if (scene.animating()) invalidate()
    }

    fun dispose() {
        disposed = true
        scene.dispose()
    }
}
