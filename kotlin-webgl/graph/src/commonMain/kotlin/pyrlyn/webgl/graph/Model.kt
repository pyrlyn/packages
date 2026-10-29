// The generic scene model: graphs of nodes and edges, grouped into a cloud. Nothing here knows the consumer's domain;
// ids are opaque strings, `group` and `weight` are free for the consumer's mapping.
package pyrlyn.webgl.graph

/** A point or vector in world units. */
data class Vec(
    val x: Double,
    val y: Double,
) {
    operator fun plus(o: Vec) = Vec(x + o.x, y + o.y)

    operator fun minus(o: Vec) = Vec(x - o.x, y - o.y)

    operator fun times(k: Double) = Vec(x * k, y * k)

    fun length() = kotlin.math.sqrt(x * x + y * y)
}

/** One node; [style] indexes the consumer's palette, [size] scales its radius. */
data class Node(
    val id: String,
    val label: String = id,
    val style: Int = 0,
    val size: Double = 1.0,
    val dim: Boolean = false,
)

/** One edge between node ids of the same graph; [dashed] and [style] are drawing hints. */
data class Edge(
    val from: String,
    val to: String,
    val style: Int = 0,
    val dashed: Boolean = false,
)

/** One graph of the cloud (a project, a host, ...): its id, title and content. */
data class Graph(
    val id: String,
    val title: String,
    val nodes: List<Node>,
    val edges: List<Edge>,
    /** Drawn as the cluster's summary colour when zoomed out. */
    val style: Int = 0,
    /** Size for the cluster radius: the node count, or a larger known total when [nodes] is a partial load. */
    val count: Int = nodes.size,
)

/** Several graphs shown together. */
data class Cloud(
    val graphs: List<Graph>,
)
