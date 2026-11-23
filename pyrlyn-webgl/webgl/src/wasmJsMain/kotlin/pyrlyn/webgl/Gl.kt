// A thin typed layer over WebGL2: only what a 2D scene needs, each call one JS call, no hidden state.
@file:OptIn(ExperimentalWasmJsInterop::class)
@file:Suppress("TooManyFunctions", "MagicNumber")

package pyrlyn.webgl

import org.w3c.dom.HTMLCanvasElement

/** The browser's `WebGL2RenderingContext`, opaque to Kotlin; [Gl] wraps it. */
external interface Gl2 : JsAny

/** A compiled shader program. */
external interface GlProgram : JsAny

/** A GPU buffer. */
external interface GlBuffer : JsAny

/** A vertex array object. */
external interface GlVertexArray : JsAny

/** A uniform location. */
external interface GlUniform : JsAny

@JsFun("(c) => c.getContext('webgl2', { antialias: true, premultipliedAlpha: false }) || null")
private external fun context2(canvas: HTMLCanvasElement): Gl2?

@JsFun(
    """(gl, vs, fs) => {
  const mk = (t, s) => { const sh = gl.createShader(t); gl.shaderSource(sh, s); gl.compileShader(sh);
    if (!gl.getShaderParameter(sh, gl.COMPILE_STATUS)) throw new Error(gl.getShaderInfoLog(sh)); return sh; };
  const p = gl.createProgram(); gl.attachShader(p, mk(gl.VERTEX_SHADER, vs)); gl.attachShader(p, mk(gl.FRAGMENT_SHADER, fs));
  gl.linkProgram(p); if (!gl.getProgramParameter(p, gl.LINK_STATUS)) throw new Error(gl.getProgramInfoLog(p)); return p; }""",
)
private external fun link(
    gl: Gl2,
    vs: String,
    fs: String,
): GlProgram

@JsFun("(gl) => gl.createBuffer()")
private external fun createBuffer(gl: Gl2): GlBuffer

@JsFun("(gl) => gl.createVertexArray()")
private external fun createVao(gl: Gl2): GlVertexArray

@JsFun("(gl, b, n) => { gl.bindBuffer(gl.ARRAY_BUFFER, b); gl.bufferData(gl.ARRAY_BUFFER, new Float32Array(n), gl.DYNAMIC_DRAW); }")
private external fun allocate(
    gl: Gl2,
    b: GlBuffer,
    n: Int,
)

@JsFun("(gl, b, i, v) => { gl.bindBuffer(gl.ARRAY_BUFFER, b); gl.bufferSubData(gl.ARRAY_BUFFER, i * 4, new Float32Array([v])); }")
private external fun putOne(
    gl: Gl2,
    b: GlBuffer,
    i: Int,
    v: Float,
)

@JsFun(
    "(gl, b, s) => { gl.bindBuffer(gl.ARRAY_BUFFER, b); gl.bufferData(gl.ARRAY_BUFFER, new Float32Array(s.split(',').map(Number)), gl.DYNAMIC_DRAW); }",
)
private external fun uploadCsv(
    gl: Gl2,
    b: GlBuffer,
    csv: String,
)

@JsFun(
    "(gl, vao, b, loc, size, stride, off) => { gl.bindVertexArray(vao); gl.bindBuffer(gl.ARRAY_BUFFER, b); gl.enableVertexAttribArray(loc); gl.vertexAttribPointer(loc, size, gl.FLOAT, false, stride * 4, off * 4); }",
)
private external fun attrib(
    gl: Gl2,
    vao: GlVertexArray,
    b: GlBuffer,
    loc: Int,
    size: Int,
    stride: Int,
    offset: Int,
)

@JsFun("(gl, p, n) => gl.getUniformLocation(p, n)")
private external fun uniform(
    gl: Gl2,
    p: GlProgram,
    name: String,
): GlUniform?

@JsFun("(gl, u, a, b, c, d) => gl.uniform4f(u, a, b, c, d)")
private external fun uniform4(
    gl: Gl2,
    u: GlUniform,
    a: Float,
    b: Float,
    c: Float,
    d: Float,
)

@JsFun("(gl, u, a) => gl.uniform1f(u, a)")
private external fun uniform1(
    gl: Gl2,
    u: GlUniform,
    a: Float,
)

@JsFun("(gl, p, vao, mode, first, count) => { gl.useProgram(p); gl.bindVertexArray(vao); gl.drawArrays(mode, first, count); }")
private external fun drawArrays(
    gl: Gl2,
    p: GlProgram,
    vao: GlVertexArray,
    mode: Int,
    first: Int,
    count: Int,
)

@JsFun(
    "(gl, w, h, r, g, b, a) => { gl.viewport(0, 0, w, h); gl.clearColor(r, g, b, a); gl.clear(gl.COLOR_BUFFER_BIT); gl.enable(gl.BLEND); gl.blendFunc(gl.SRC_ALPHA, gl.ONE_MINUS_SRC_ALPHA); }",
)
private external fun begin(
    gl: Gl2,
    w: Int,
    h: Int,
    r: Float,
    g: Float,
    b: Float,
    a: Float,
)

/** Primitive modes for [Gl.draw]. */
enum class Mode(
    val gl: Int,
) {
    POINTS(0),
    LINES(1),
    TRIANGLES(4),
}

/** An RGBA colour, components 0..1. */
data class Rgba(
    val r: Float,
    val g: Float,
    val b: Float,
    val a: Float = 1f,
)

/** A WebGL2 context on a canvas. */
class Gl private constructor(
    val raw: Gl2,
) {
    /** Clears the drawing buffer of [width]×[height] pixels to [color] and sets alpha blending. */
    fun begin(
        width: Int,
        height: Int,
        color: Rgba,
    ) = begin(raw, width, height, color.r, color.g, color.b, color.a)

    /** Compiles and links [vertex] and [fragment] GLSL ES 3.00 sources; throws with the driver's log on error. */
    fun program(
        vertex: String,
        fragment: String,
    ): Program = Program(this, link(raw, vertex, fragment))

    /** A new float buffer. */
    fun buffer(): FloatBuffer = FloatBuffer(this, createBuffer(raw))

    /** A new vertex array. */
    fun vertexArray(): VertexArray = VertexArray(this, createVao(raw))

    /** Draws [count] vertices of [vao] with [program]. */
    fun draw(
        program: Program,
        vao: VertexArray,
        mode: Mode,
        count: Int,
        first: Int = 0,
    ) {
        if (count > 0) drawArrays(raw, program.raw, vao.raw, mode.gl, first, count)
    }

    companion object {
        /** The WebGL2 context of [canvas], or null when the browser has none. */
        fun of(canvas: HTMLCanvasElement): Gl? = context2(canvas)?.let(::Gl)
    }
}

/** A linked program and its uniforms. */
class Program internal constructor(
    private val gl: Gl,
    internal val raw: GlProgram,
) {
    private val cache = HashMap<String, GlUniform?>()

    private fun loc(name: String) = cache.getOrPut(name) { uniform(gl.raw, raw, name) }

    /** Sets a `vec4` uniform. */
    fun set(
        name: String,
        a: Float,
        b: Float,
        c: Float,
        d: Float,
    ) {
        loc(name)?.let { uniform4(gl.raw, it, a, b, c, d) }
    }

    /** Sets a `float` uniform. */
    fun set(
        name: String,
        a: Float,
    ) {
        loc(name)?.let { uniform1(gl.raw, it, a) }
    }
}

/** A buffer of floats. */
class FloatBuffer internal constructor(
    private val gl: Gl,
    internal val raw: GlBuffer,
) {
    /** Replaces the contents with [data]. */
    fun upload(data: FloatArray) {
        if (data.isEmpty()) {
            allocate(gl.raw, raw, 0)
            return
        }
        // One JS call: a comma-joined string crosses the boundary cheaper than one call per float.
        uploadCsv(gl.raw, raw, data.joinToString(","))
    }

    /** Overwrites one float at [index]. */
    fun put(
        index: Int,
        value: Float,
    ) = putOne(gl.raw, raw, index, value)
}

/** A vertex array: attribute bindings to buffers. */
class VertexArray internal constructor(
    private val gl: Gl,
    internal val raw: GlVertexArray,
) {
    /** Binds attribute [location] to [size] floats of [buffer], every [stride] floats from [offset]. */
    fun attribute(
        location: Int,
        buffer: FloatBuffer,
        size: Int,
        stride: Int,
        offset: Int = 0,
    ) = attrib(gl.raw, raw, buffer.raw, location, size, stride, offset)
}
