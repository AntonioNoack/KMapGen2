package amitp.mapgen2

import amitp.mapgen2.graphbuilder.GridVoronoi
import amitp.mapgen2.graphbuilder.Voronoi
import amitp.mapgen2.graphbuilder.VoronoiGraphBuilder.Companion.buildGraph
import amitp.mapgen2.pointselector.RandomPointSelector
import amitp.mapgen2.structures.CornerList
import me.anno.maths.MinMax.max
import me.anno.maths.MinMax.median
import me.anno.maths.MinMax.min
import me.anno.utils.assertions.assertEquals
import org.joml.Vector2f
import org.joml.Vector3i
import org.junit.jupiter.api.Test
import java.awt.Color
import java.awt.Graphics2D
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO

class GridVoronoiTest {

    fun getCorners(corners: CornerList): Set<Vector3i> {
        val result = HashSet<Vector3i>()
        for (corner in corners.indices) {
            forEachCell(corners, corner) { v1, v2, v3 ->
                result.add(Vector3i(v1, v2, v3))
            }
        }
        return result
    }

    fun forEachCell(corners: CornerList, corner: Int, callback: (Int, Int, Int) -> Unit) {
        val numCells = corners.cells.getSize(corner)
        // all combinations of triples
        for (i in 2 until numCells) {
            for (j in 1 until i) {
                for (k in 0 until j) {
                    val vi = corners.cells[corner, i]
                    val vj = corners.cells[corner, j]
                    val vk = corners.cells[corner, k]
                    val v1 = min(vi, vj, vk)
                    val v2 = median(vi, vj, vk)
                    val v3 = max(vi, vj, vk)
                    callback(v1, v2, v3)
                }
            }
        }
    }

    @Test
    fun testGridVoronoiAgainstBaseline() {
        // generate centers
        // use grid-voronoi and baseline
        // todo compare all corners and edges, which are within the defined bounds
        val n = 100
        val size = 1f
        val points = RandomPointSelector.select(size, n, 1234)

        val grid = GridVoronoi(points, size)
        val actual = grid.map
        val expected = run {
            val voronoi = Voronoi(points, size)
            buildGraph(points, voronoi, size)
        }

        println("Grid Size: ${grid.numPointsX} x ${grid.numPointsY}")
        println("Actual: ${actual.corners.size} corners + ${actual.edges.size} edges")
        println("Expected: ${expected.corners.size} corners + ${expected.edges.size} edges")

        val actualCorners = getCorners(actual.corners)

        // render expected corners and edges for debugging
        showGrid(grid, size, actual, "actualGrid")
        showGrid(grid, size, expected, "expectedGrid")

        var failed = 0
        for (corner in expected.corners.indices) {
            val point = Vector2f(
                expected.corners.getPointX(corner),
                expected.corners.getPointY(corner)
            )
            forEachCell(expected.corners, corner) { v1, v2, v3 ->
                val key = Vector3i(v1, v2, v3)
                if (key !in actualCorners) {

                    failed++
                    val cells = ArrayList<Int>()
                    expected.corners.cells.forEach(corner) { cell -> cells.add(cell) }
                    cells.sort()

                    println("Missing corner #$corner, $cells, (${(point.x * grid.numPointsX)},${point.y * grid.numPointsY})")

                } else {
                    // todo compare edges and cells
                }
            }
        }

        assertEquals(0, failed, "Failures!")

    }

    fun showGrid(grid: GridVoronoi, size: Float, map: GeneratedMap, name: String) {
        val scale = 50f
        val imgSize = (scale * max(grid.numPointsX, grid.numPointsY)).toInt()
        val img = BufferedImage(imgSize, imgSize, 1)
        val g = img.graphics as Graphics2D
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)

        val corners = map.corners
        val edges = map.edges
        val scaleI = imgSize / size
        for (edge in edges.indices) {
            val v0 = edges.getCornerA(edge)
            val v1 = edges.getCornerB(edge)
            if (v0 < 0 || v1 < 0) continue

            val x0 = (corners.getPointX(v0) * scaleI).toInt()
            val y0 = (corners.getPointY(v0) * scaleI).toInt()
            val x1 = (corners.getPointX(v1) * scaleI).toInt()
            val y1 = (corners.getPointY(v1) * scaleI).toInt()

            g.color = Color.WHITE
            g.drawLine(x0, y0, x1, y1)

            g.color = Color.RED
            g.drawRect(x0, y0, 1, 1)
            g.drawRect(x1, y1, 1, 1)
        }

        for (corner in corners.indices) {
            val x0 = (corners.getPointX(corner) * scaleI).toInt()
            val y0 = (corners.getPointY(corner) * scaleI).toInt()

            g.color = Color.CYAN
            g.fillRect(x0 - 1, y0 - 1, 3, 3)
        }

        val cells = map.cells
        for (cell in cells.indices) {
            val x0 = (cells.getPointX(cell) * scaleI).toInt()
            val y0 = (cells.getPointY(cell) * scaleI).toInt()

            g.color = Color.ORANGE
            g.fillRect(x0 - 1, y0 - 1, 3, 3)
        }

        ImageIO.write(img, "png", File(desktop, "$name.png"))
    }
}