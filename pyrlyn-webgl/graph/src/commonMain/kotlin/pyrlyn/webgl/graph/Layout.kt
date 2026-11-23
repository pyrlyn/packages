// Layout: a deterministic force layout inside each graph, then clusters packed side by side into the cloud.
@file:Suppress("MagicNumber")

package pyrlyn.webgl.graph

import kotlin.math.PI
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

private const val ITERATIONS = 150
private const val START_TEMP = 0.1
private const val COOL = 0.96
private const val MIN_DIST = 1e-3
private const val RING = 0.4

/**
 * Node positions of one graph in the unit square, centred on (0.5, 0.5): Fruchterman–Reingold from a circle, no
 * randomness, so the same graph always lands the same way.
 */
fun forceLayout(graph: Graph): Map<String, Vec> {
    val ids = graph.nodes.map { it.id }
    val n = ids.size
    if (n <= 1) return ids.associateWith { Vec(0.5, 0.5) }
    val index = ids.withIndex().associate { (i, id) -> id to i }
    val x = DoubleArray(n) { 0.5 + RING * cos(2 * PI * it / n) }
    val y = DoubleArray(n) { 0.5 + RING * sin(2 * PI * it / n) }
    val links =
        graph.edges
            .mapNotNull { e ->
                index[e.from]?.let { a -> index[e.to]?.let { b -> a to b } }
            }.filter { it.first != it.second }
    val k = sqrt(1.0 / n)
    var t = START_TEMP
    repeat(ITERATIONS) {
        val dx = DoubleArray(n)
        val dy = DoubleArray(n)
        for (i in 0 until n) {
            for (j in i + 1 until n) {
                val ux = x[i] - x[j]
                val uy = y[i] - y[j]
                val d = max(sqrt(ux * ux + uy * uy), MIN_DIST)
                val f = k * k / d
                dx[i] += ux / d * f
                dy[i] += uy / d * f
                dx[j] -= ux / d * f
                dy[j] -= uy / d * f
            }
        }
        for ((a, b) in links) {
            val ux = x[a] - x[b]
            val uy = y[a] - y[b]
            val d = max(sqrt(ux * ux + uy * uy), MIN_DIST)
            val f = d * d / k
            dx[a] -= ux / d * f
            dy[a] -= uy / d * f
            dx[b] += ux / d * f
            dy[b] += uy / d * f
        }
        for (i in 0 until n) {
            val d = max(sqrt(dx[i] * dx[i] + dy[i] * dy[i]), MIN_DIST)
            x[i] += dx[i] / d * min(d, t)
            y[i] += dy[i] / d * min(d, t)
        }
        t *= COOL
    }
    return normalize(ids, x, y)
}

private fun normalize(
    ids: List<String>,
    x: DoubleArray,
    y: DoubleArray,
): Map<String, Vec> {
    val cx = (x.max() + x.min()) / 2
    val cy = (y.max() + y.min()) / 2
    // Fit into the disc of radius RING, so every node stays inside its cluster circle.
    val reach = max(x.indices.maxOf { sqrt((x[it] - cx) * (x[it] - cx) + (y[it] - cy) * (y[it] - cy)) }, MIN_DIST)
    return ids.withIndex().associate { (i, id) -> id to Vec(0.5 + (x[i] - cx) / reach * RING, 0.5 + (y[i] - cy) / reach * RING) }
}

/** One placed cluster: its world [center] and [radius], and node positions in world units. */
data class ClusterPlace(
    val graph: Graph,
    val center: Vec,
    val radius: Double,
    val nodes: Map<String, Vec>,
)

/** The whole cloud in world units, with its bounding box. */
data class CloudLayout(
    val clusters: List<ClusterPlace>,
    val min: Vec,
    val max: Vec,
) {
    /** The cluster with [id], or null. */
    fun cluster(id: String): ClusterPlace? = clusters.firstOrNull { it.graph.id == id }
}

/** Gap between clusters, in units of the largest radius. */
private const val GAP = 0.35

/**
 * Packs the clusters side by side: each graph gets a radius growing with the square root of its node count (so area
 * tracks size), then clusters go into rows of a near-square grid, largest first, each row as tall as its largest.
 */
fun layoutCloud(
    cloud: Cloud,
    inner: (Graph) -> Map<String, Vec> = ::forceLayout,
): CloudLayout {
    if (cloud.graphs.isEmpty()) return CloudLayout(emptyList(), Vec(0.0, 0.0), Vec(0.0, 0.0))
    val sized = cloud.graphs.map { it to clusterRadius(it) }.sortedByDescending { it.second }
    val gap = sized.first().second * GAP
    val perRow = ceil(sqrt(sized.size.toDouble())).toInt()
    val placed = ArrayList<ClusterPlace>()
    var y = 0.0
    sized.chunked(perRow).forEach { row ->
        val rowR = row.maxOf { it.second }
        var x = 0.0
        row.forEach { (g, r) ->
            val c = Vec(x + r, y + rowR)
            val pos = inner(g).mapValues { (_, p) -> Vec(c.x + (p.x - 0.5) * 2 * r, c.y + (p.y - 0.5) * 2 * r) }
            placed += ClusterPlace(g, c, r, pos)
            x += 2 * r + gap
        }
        y += 2 * rowR + gap
    }
    val minV = Vec(placed.minOf { it.center.x - it.radius }, placed.minOf { it.center.y - it.radius })
    val maxV = Vec(placed.maxOf { it.center.x + it.radius }, placed.maxOf { it.center.y + it.radius })
    // Keep the consumer's order for drawing and picking.
    val order = cloud.graphs.withIndex().associate { (i, g) -> g.id to i }
    return CloudLayout(placed.sortedBy { order[it.graph.id] }, minV, maxV)
}

/** A cluster's radius: 1 for an empty or single-node graph, growing as √n of its [Graph.count]. */
fun clusterRadius(g: Graph): Double = max(1.0, sqrt(max(g.count, g.nodes.size).toDouble()))
