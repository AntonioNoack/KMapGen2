package amitp.mapgen2.graphs

import amitp.mapgen2.graphbuilder.perfect.PerfectRectangleGridBuilder
import amitp.mapgen2.voronoi.GridVoronoiTest.Companion.debugRenderGraph
import org.joml.Vector2f
import org.junit.jupiter.api.Test

class RectangleGridTest {
    @Test
    fun testRectHexagonGrid() {
        val builder = PerfectRectangleGridBuilder()
        val actual = builder.buildGraph(Vector2f(1000f), 1000, 1)
        debugRenderGraph(actual, "rectGrid")
    }
}