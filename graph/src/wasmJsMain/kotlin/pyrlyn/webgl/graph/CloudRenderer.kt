// The WebGL2 renderer of a graph cloud: cluster summaries when zoomed out, nodes and edges when zoomed in, drag to
// pan, wheel to zoom about the cursor, click a cluster to fly into it, click a node to hand it to the consumer.
@file:Suppress("MagicNumber")

package pyrlyn.webgl.graph

import pyrlyn.webgl.FloatBuffer
import pyrlyn.webgl.Gl
import pyrlyn.webgl.Mode
import pyrlyn.webgl.Program
import pyrlyn.webgl.Rgba
import pyrlyn.webgl.VertexArray

private const val VS = """#version 300 es
layout(location = 0) in vec2 a_pos; layout(location = 1) in float a_size; layout(location = 2) in vec4 a_color;
uniform vec4 u_view; uniform vec4 u_res;
out vec4 v_color; out float v_ring;
void main() {
  vec2 s = (a_pos - u_view.xy) * u_view.z;
  gl_Position = vec4(s.x / (u_res.x * 0.5), -s.y / (u_res.y * 0.5), 0.0, 1.0);
  gl_PointSize = max(a_size * u_res.z, 1.0);
  v_color = a_color; v_ring = u_res.w;
}"""

private const val FS = """#version 300 es
precision mediump float;
in vec4 v_color; in float v_ring; out vec4 o;
uniform float u_points;
void main() {
  if (u_points > 0.5) {
    vec2 d = gl_PointCoord * 2.0 - 1.0; float r = dot(d, d);
    if (r > 1.0) discard;
    o = vec4(v_color.rgb, v_color.a * (1.0 - smoothstep(0.85, 1.0, r)));
  } else { o = v_color; }
}"""

private const val STRIDE = 7

/** Colours by style index, wrapping; the consumer supplies its palette. */
class CloudPalette(
    val background: Rgba,
    val styles: List<Rgba>,
    val edge: Rgba,
    val summaryAlpha: Float = 0.35f,
) {
    fun style(i: Int): Rgba = styles[((i % styles.size) + styles.size) % styles.size]
}

/** Callbacks into the consumer. */
interface CloudListener {
    /** A cluster was entered (fly-to finished or started); null when back at the whole cloud. */
    fun focused(cluster: String?) = Unit

    /** A node of a fully drawn cluster was clicked. */
    fun nodeClicked(
        cluster: String,
        node: String,
    ) = Unit
}

/** Holds the cloud, its layout and camera, and draws it. Platform-free state is in [CloudState]. */
class CloudRenderer(
    val state: CloudState,
    var palette: CloudPalette,
) {
    private var program: Program? = null
    private var buffer: FloatBuffer? = null
    private var vao: VertexArray? = null

    fun init(gl: Gl): Boolean {
        val p = runCatching { gl.program(VS, FS) }.getOrNull() ?: return false
        val b = gl.buffer()
        val v = gl.vertexArray()
        v.attribute(0, b, 2, STRIDE, 0)
        v.attribute(1, b, 1, STRIDE, 2)
        v.attribute(2, b, 4, STRIDE, 3)
        program = p
        buffer = b
        vao = v
        return true
    }

    fun frame(
        gl: Gl,
        width: Int,
        height: Int,
        dpr: Double,
        timeMs: Double,
    ) {
        val cam = state.camera
        cam.width = width / dpr
        cam.height = height / dpr
        state.tick(timeMs)
        gl.begin(width, height, palette.background)
        val p = program
        val b = buffer
        val v = vao
        if (p == null || b == null || v == null) return
        val batch = state.batch(palette)
        p.set(
            "u_view",
            cam.view.center.x
                .toFloat(),
            cam.view.center.y
                .toFloat(),
            (cam.view.scale * dpr).toFloat(),
            0f,
        )
        p.set("u_res", width.toFloat(), height.toFloat(), dpr.toFloat(), 0f)
        b.upload(batch.lines + batch.points)
        p.set("u_points", 0f)
        gl.draw(p, v, Mode.LINES, batch.lines.size / STRIDE)
        p.set("u_points", 1f)
        gl.draw(p, v, Mode.POINTS, batch.points.size / STRIDE, first = batch.lines.size / STRIDE)
    }
}

/** Vertex data of one frame: edge lines then points (summary discs and nodes), [STRIDE] floats each. */
class Batch(
    val lines: FloatArray,
    val points: FloatArray,
)

/** The cloud's interactive state: data, layout, camera, focus and input handling. */
class CloudState(
    cloud: Cloud,
    var listener: CloudListener = object : CloudListener {},
) {
    val camera = Camera()
    var layout: CloudLayout = layoutCloud(cloud)
        private set
    var focused: String? = null
        private set
    private var fitted = false
    private var now = 0.0
    private var press: Vec? = null
    private var last: Vec? = null
    private var moved = false

    /** Replaces the data; the layout is recomputed and the focus kept when its cluster still exists. */
    fun update(cloud: Cloud) {
        layout = layoutCloud(cloud)
        if (focused != null && layout.cluster(focused!!) == null) focused = null
    }

    /** Flies into cluster [id]. */
    fun enter(id: String) {
        val c = layout.cluster(id) ?: return
        focused = id
        camera.flyTo(camera.fit(c.center - Vec(c.radius, c.radius), c.center + Vec(c.radius, c.radius)), now)
        listener.focused(id)
    }

    /** Flies back to the whole cloud. */
    fun back() {
        focused = null
        camera.flyTo(camera.fit(layout.min, layout.max), now)
        listener.focused(null)
    }

    fun tick(nowMs: Double) {
        now = nowMs
        if (!fitted && camera.width > 1) {
            camera.view = camera.fit(layout.min, layout.max)
            fitted = true
        }
        camera.tick(nowMs)
    }

    val animating: Boolean get() = camera.flying

    /** Pointer down at screen [at]. */
    fun down(at: Vec) {
        press = at
        last = at
        moved = false
    }

    /** Pointer move; pans while [dragging]. True when the view changed. */
    fun move(
        at: Vec,
        dragging: Boolean,
    ): Boolean {
        val l = last
        val p = press
        if (!dragging || l == null || p == null) return false
        if ((at - p).length() > DRAG_PX) moved = true
        camera.pan(at.x - l.x, at.y - l.y)
        last = at
        return true
    }

    /** Pointer up: a click (no drag) picks; true when anything changed. */
    fun up(at: Vec): Boolean {
        val wasClick = press != null && !moved
        press = null
        last = null
        val h = if (wasClick) pick(layout, camera, at, focused) else null
        when (h) {
            is Hit.Node -> listener.nodeClicked(h.cluster, h.id)
            is Hit.Cluster -> if (h.id != focused) enter(h.id)
            null -> Unit
        }
        return h != null
    }

    /** Wheel zoom about [at]; [delta] > 0 zooms out. */
    fun wheel(
        at: Vec,
        delta: Double,
    ): Boolean {
        camera.zoom(kotlin.math.exp(-delta * WHEEL), at)
        return true
    }

    /** The vertices for this frame. */
    fun batch(palette: CloudPalette): Batch {
        val lines = ArrayList<Float>()
        val points = ArrayList<Float>()

        fun put(
            to: MutableList<Float>,
            p: Vec,
            size: Double,
            c: Rgba,
        ) {
            to += p.x.toFloat()
            to += p.y.toFloat()
            to += size.toFloat()
            to += c.r
            to += c.g
            to += c.b
            to += c.a
        }
        for (c in layout.clusters) {
            val tone = palette.style(c.graph.style)
            if (detailOf(c, camera, focused) == Detail.SUMMARY) {
                put(points, c.center, c.radius * 2 * camera.view.scale, tone.copy(a = palette.summaryAlpha))
                continue
            }
            put(points, c.center, c.radius * 2 * camera.view.scale, tone.copy(a = RING_ALPHA))
            full(c, palette, { p, s, col -> put(lines, p, s, col) }, { p, s, col -> put(points, p, s, col) })
        }
        return Batch(lines.toFloatArray(), points.toFloatArray())
    }

    private fun full(
        c: ClusterPlace,
        palette: CloudPalette,
        line: (Vec, Double, Rgba) -> Unit,
        point: (Vec, Double, Rgba) -> Unit,
    ) {
        for (e in c.graph.edges) {
            val a = c.nodes[e.from]
            val b = c.nodes[e.to]
            if (a == null || b == null) continue
            val col = if (e.style == 0) palette.edge else palette.style(e.style)
            line(a, 1.0, col)
            line(b, 1.0, col)
        }
        for (n in c.graph.nodes) {
            val p = c.nodes[n.id] ?: continue
            val col = palette.style(n.style)
            point(p, NODE_PX * n.size, if (n.dim) col.copy(a = DIM_ALPHA) else col)
        }
    }

    private companion object {
        const val DRAG_PX = 4.0
        const val WHEEL = 0.0015
        const val NODE_PX = 9.0
        const val RING_ALPHA = 0.08f
        const val DIM_ALPHA = 0.45f
    }
}
