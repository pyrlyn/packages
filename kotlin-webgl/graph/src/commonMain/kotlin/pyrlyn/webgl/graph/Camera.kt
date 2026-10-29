// The 2D camera: world ↔ screen, pan, zoom about a point, fit to a box, and an eased fly-to.
@file:Suppress("MagicNumber")

package pyrlyn.webgl.graph

import kotlin.math.max
import kotlin.math.min

/** Where the camera looks: world [center] and pixels per world unit ([scale]). */
data class View(
    val center: Vec,
    val scale: Double,
)

/** A camera over a viewport of [width]×[height] screen units (CSS pixels). */
class Camera(
    var view: View = View(Vec(0.0, 0.0), 1.0),
    var width: Double = 1.0,
    var height: Double = 1.0,
    val minScale: Double = 1e-3,
    val maxScale: Double = 1e4,
) {
    private var flight: Flight? = null

    fun toScreen(w: Vec): Vec = Vec((w.x - view.center.x) * view.scale + width / 2, (w.y - view.center.y) * view.scale + height / 2)

    fun toWorld(s: Vec): Vec = Vec((s.x - width / 2) / view.scale + view.center.x, (s.y - height / 2) / view.scale + view.center.y)

    /** Moves the view by a screen-space drag of [dx], [dy]. */
    fun pan(
        dx: Double,
        dy: Double,
    ) {
        flight = null
        view = view.copy(center = view.center - Vec(dx, dy) * (1 / view.scale))
    }

    /** Zooms by [factor] keeping the world point under screen [at] fixed. */
    fun zoom(
        factor: Double,
        at: Vec,
    ) {
        flight = null
        val before = toWorld(at)
        view = view.copy(scale = (view.scale * factor).coerceIn(minScale, maxScale))
        val after = toWorld(at)
        view = view.copy(center = view.center + (before - after))
    }

    /** The view that shows the box [min]..[max] with [margin] (share of the viewport) around it. */
    fun fit(
        min: Vec,
        max: Vec,
        margin: Double = 0.08,
    ): View {
        val w = max(max.x - min.x, 1e-6)
        val h = max(max.y - min.y, 1e-6)
        val s = min(width / w, height / h) * (1 - 2 * margin)
        return View((min + max) * 0.5, s.coerceIn(minScale, maxScale))
    }

    /** Starts an eased flight to [target] lasting [durationMs], from time [nowMs]. */
    fun flyTo(
        target: View,
        nowMs: Double,
        durationMs: Double = 450.0,
    ) {
        flight = Flight(view, target, nowMs, durationMs)
    }

    /** Advances a flight to [nowMs]; true while still flying. */
    fun tick(nowMs: Double): Boolean {
        val f = flight ?: return false
        val t = ((nowMs - f.start) / f.duration).coerceIn(0.0, 1.0)
        view = if (t >= 1.0) f.to else f.at(ease(t))
        if (t >= 1.0) flight = null
        return flight != null
    }

    val flying: Boolean get() = flight != null
}

private class Flight(
    val from: View,
    val to: View,
    val start: Double,
    val duration: Double,
) {
    /** Centre interpolates linearly, scale geometrically, so a zoom feels even. */
    fun at(t: Double): View {
        val s = from.scale * kotlin.math.exp(kotlin.math.ln(to.scale / from.scale) * t)
        return View(from.center + (to.center - from.center) * t, s)
    }
}

/** Ease-in-out cubic on 0..1. */
fun ease(t: Double): Double = if (t < 0.5) 4 * t * t * t else 1 - (-2 * t + 2).let { it * it * it } / 2
