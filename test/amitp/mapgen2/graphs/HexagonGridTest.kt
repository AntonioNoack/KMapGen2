package amitp.mapgen2.graphs

import amitp.mapgen2.graphbuilder.perfect.HexHexagonGridBuilder
import amitp.mapgen2.graphbuilder.perfect.RectHexagonGridBuilder
import amitp.mapgen2.voronoi.GridVoronoiTest
import amitp.mapgen2.voronoi.GridVoronoiTest.Companion.debugRenderGraph
import org.joml.Vector2f
import org.junit.jupiter.api.Test

class HexagonGridTest {
    @Test
    fun testRectHexagonGrid() {
        val builder = RectHexagonGridBuilder()
        val actual = builder.buildGraph(Vector2f(1000f), 1000, 1)
        debugRenderGraph(actual, "rectHexGrid")
    }

    @Test
    fun testHexHexagonGrid() {
        val builder = HexHexagonGridBuilder()
        val actual = builder.buildGraph(Vector2f(1000f), 1000, 1)
        debugRenderGraph(actual, "hexHexGrid")
    }
}