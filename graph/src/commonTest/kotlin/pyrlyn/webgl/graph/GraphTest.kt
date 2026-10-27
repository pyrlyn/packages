package pyrlyn.webgl.graph

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class GraphTest {
    private fun chain(
        id: String,
        n: Int,
    ) = Graph(id, id, List(n) { Node("$id$it") }, List(n - 1) { Edge("$id$it", "$id${it + 1}") })

    @Test
    fun forceLayoutIsDeterministicAndInsideTheUnitSquare() {
        val g = chain("a", 12)
        val a = forceLayout(g)
        assertEquals(a, forceLayout(g))
        assertTrue(a.values.all { it.x in 0.0..1.0 && it.y in 0.0..1.0 })
    }

    @Test
    fun cloudClustersDoNotOverlapAndKeepOrder() {
        val cloud = Cloud(listOf(chain("a", 4), chain("b", 25), chain("c", 1), chain("d", 9)))
        val l = layoutCloud(cloud)
        assertEquals(listOf("a", "b", "c", "d"), l.clusters.map { it.graph.id })
        for (i in l.clusters.indices) {
            for (j in i + 1 until l.clusters.size) {
                val p = l.clusters[i]
                val q = l.clusters[j]
                assertTrue((p.center - q.center).length() >= p.radius + q.radius, "${p.graph.id} overlaps ${q.graph.id}")
            }
        }
        l.clusters.forEach { c -> assertTrue(c.nodes.values.all { (it - c.center).length() <= c.radius * 1.0001 }) }
    }

    @Test
    fun zoomKeepsThePointUnderTheCursor() {
        val cam = Camera(View(Vec(5.0, 5.0), 2.0), 800.0, 600.0)
        val at = Vec(100.0, 50.0)
        val before = cam.toWorld(at)
        cam.zoom(3.0, at)
        val after = cam.toWorld(at)
        assertTrue(abs(before.x - after.x) < 1e-9 && abs(before.y - after.y) < 1e-9)
    }

    @Test
    fun flyToEndsExactlyOnTheTarget() {
        val cam = Camera(View(Vec(0.0, 0.0), 1.0), 100.0, 100.0)
        val target = View(Vec(10.0, -4.0), 8.0)
        cam.flyTo(target, 0.0, 400.0)
        assertTrue(cam.tick(200.0))
        assertTrue(cam.view.scale > 1.0 && cam.view.scale < 8.0)
        assertTrue(!cam.tick(400.0))
        assertEquals(target, cam.view)
    }

    @Test
    fun lodSwitchesWithScreenRadiusAndFocus() {
        val l = layoutCloud(Cloud(listOf(chain("a", 16))))
        val c = l.clusters.single()
        val cam = Camera(View(c.center, 1.0), 800.0, 600.0)
        assertEquals(Detail.SUMMARY, detailOf(c, cam))
        assertEquals(Detail.FULL, detailOf(c, cam, focused = "a"))
        cam.view = cam.view.copy(scale = FULL_DETAIL_PX / c.radius)
        assertEquals(Detail.FULL, detailOf(c, cam))
    }

    @Test
    fun pickPrefersNodesInFullClustersElseTheCluster() {
        val l = layoutCloud(Cloud(listOf(chain("a", 4), chain("b", 4))))
        val a = l.cluster("a")!!
        val cam = Camera(width = 800.0, height = 600.0)
        cam.view = cam.fit(l.min, l.max)
        val zoomedOut = cam.view.copy(scale = 1.0)
        cam.view = zoomedOut
        assertIs<Hit.Cluster>(pick(l, cam, cam.toScreen(a.center)))
        cam.view = View(a.center, 400.0)
        val node = a.nodes.entries.first()
        assertEquals(Hit.Node("a", node.key), pick(l, cam, cam.toScreen(node.value)))
        assertNull(pick(l, cam, Vec(-10_000.0, -10_000.0)))
    }
}
