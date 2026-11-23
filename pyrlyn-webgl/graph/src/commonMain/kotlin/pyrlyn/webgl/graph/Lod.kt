// Level of detail and picking over a laid-out cloud.
package pyrlyn.webgl.graph

/** How a cluster is drawn at the current zoom. */
enum class Detail {
    /** A disc with the node count: the cluster is too small on screen for its nodes. */
    SUMMARY,

    /** Nodes and edges. */
    FULL,
}

/** Screen radius (px) from which a cluster shows its nodes. */
const val FULL_DETAIL_PX = 90.0

/** The detail of [c] at camera [cam]; a focused cluster is always [Detail.FULL]. */
fun detailOf(
    c: ClusterPlace,
    cam: Camera,
    focused: String? = null,
    threshold: Double = FULL_DETAIL_PX,
): Detail = if (c.graph.id == focused || c.radius * cam.view.scale >= threshold) Detail.FULL else Detail.SUMMARY

/** What a pointer hit. */
sealed interface Hit {
    data class Cluster(
        val id: String,
    ) : Hit

    data class Node(
        val cluster: String,
        val id: String,
    ) : Hit
}

/** Node hit radius in screen pixels. */
const val NODE_HIT_PX = 10.0

/**
 * What is under screen point [at]: a node of a fully drawn cluster within [nodePx], else the cluster whose disc
 * contains the point, else null.
 */
fun pick(
    layout: CloudLayout,
    cam: Camera,
    at: Vec,
    focused: String? = null,
    nodePx: Double = NODE_HIT_PX,
): Hit? {
    val w = cam.toWorld(at)
    var best: Hit.Node? = null
    var bestD = nodePx
    for (c in layout.clusters) {
        if (detailOf(c, cam, focused) != Detail.FULL) continue
        for ((id, p) in c.nodes) {
            val d = (cam.toScreen(p) - at).length()
            if (d <= bestD) {
                bestD = d
                best = Hit.Node(c.graph.id, id)
            }
        }
    }
    if (best != null) return best
    return layout.clusters.firstOrNull { (it.center - w).length() <= it.radius }?.let { Hit.Cluster(it.graph.id) }
}
