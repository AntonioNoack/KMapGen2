package amitp.mapgen2.voronoi

import amitp.mapgen2.GeneratedMap
import amitp.mapgen2.GraphValidator.validateGraph
import amitp.mapgen2.desktop
import amitp.mapgen2.graphbuilder.VoronoiGraphBuilder
import amitp.mapgen2.graphbuilder.voronoi.GridVoronoi
import amitp.mapgen2.graphbuilder.voronoi.Voronoi
import amitp.mapgen2.pointselector.GridPointSelector
import amitp.mapgen2.structures.CornerList
import me.anno.maths.MinMax
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

    companion object {
        fun debugRenderGraph(map: GeneratedMap, name: String) {
            val size = map.size
            val imgSize = 1024
            val img = BufferedImage(imgSize, imgSize, 1)
            val g = img.graphics as Graphics2D
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)

            val corners = map.corners
            val edges = map.edges
            val cells = map.cells

            val scaleX = (imgSize - 1) / size.x
            val scaleY = (imgSize - 1) / size.y
            for (edge in edges.indices) {
                val v0 = edges.getCornerA(edge)
                val v1 = edges.getCornerB(edge)
                if (v0 < 0 || v1 < 0) continue

                val x0 = (corners.getPointX(v0) * scaleX).toInt()
                val y0 = (corners.getPointY(v0) * scaleY).toInt()
                val x1 = (corners.getPointX(v1) * scaleX).toInt()
                val y1 = (corners.getPointY(v1) * scaleY).toInt()

                g.color = Color.WHITE
                g.drawLine(x0, y0, x1, y1)

                g.color = Color.RED
                g.drawRect(x0, y0, 1, 1)
                g.drawRect(x1, y1, 1, 1)

                val xm = (x0 + x1) shr 1
                val ym = (y0 + y1) shr 1

                g.color = Color.LIGHT_GRAY
                val cellA = edges.getCellA(edge)
                val cellB = edges.getCellB(edge)
                if (cellA >= 0) {
                    val x1 = (cells.getPointX(cellA) * scaleX).toInt()
                    val y1 = (cells.getPointY(cellA) * scaleY).toInt()
                    g.drawLine(xm, ym, x1, y1)
                }
                if (cellB >= 0) {
                    val x1 = (cells.getPointX(cellB) * scaleX).toInt()
                    val y1 = (cells.getPointY(cellB) * scaleY).toInt()
                    g.drawLine(xm, ym, x1, y1)
                }
            }

            for (corner in corners.indices) {
                val x0 = (corners.getPointX(corner) * scaleX).toInt()
                val y0 = (corners.getPointY(corner) * scaleY).toInt()

                g.color = if (corners.isBorder(corner)) Color.MAGENTA else Color.CYAN
                g.fillRect(x0 - 1, y0 - 1, 3, 3)

                g.color = Color.GRAY
                corners.cells.forEach(corner) { cell ->
                    val x1 = (cells.getPointX(cell) * scaleX).toInt()
                    val y1 = (cells.getPointY(cell) * scaleY).toInt()
                    g.drawLine(x0, y0, x1, y1)
                }
            }

            for (cell in cells.indices) {
                val x0 = (cells.getPointX(cell) * scaleX).toInt()
                val y0 = (cells.getPointY(cell) * scaleY).toInt()

                g.color = Color.ORANGE
                g.fillRect(x0 - 1, y0 - 1, 3, 3)
            }

            ImageIO.write(img, "png", File(desktop, "$name.png"))
        }
    }

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
                    val v1 = MinMax.min(vi, vj, vk)
                    val v2 = MinMax.median(vi, vj, vk)
                    val v3 = MinMax.max(vi, vj, vk)
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
        val size = Vector2f(1000f)
        val points = GridPointSelector(0.9f).select(size, n, 8541)

        val grid = GridVoronoi(points, size)
        val actual = grid.map
        println("Validating GridVoronoi")
        validateGraph(actual)

        val expected = run {
            val voronoi = Voronoi(points, size)
            VoronoiGraphBuilder.buildGraph(points, voronoi, size)
        }
        println("Validating VoronoiGraphBuilder")
        validateGraph(expected)

        println("Grid Size: ${grid.numCellsX} x ${grid.numCellsY}")
        println("Actual: ${actual.corners.size} corners + ${actual.edges.size} edges")
        println("Expected: ${expected.corners.size} corners + ${expected.edges.size} edges")

        val actualCorners = getCorners(actual.corners)

        // render expected corners and edges for debugging
        debugRenderGraph(actual, "actualGrid")
        debugRenderGraph(expected, "expectedGrid")

        var failed = 0
        for (corner in expected.corners.indices) {
            val point = Vector2f(
                expected.corners.getPointX(corner),
                expected.corners.getPointY(corner)
            )
            forEachCell(expected.corners, corner) { v1, v2, v3 ->
                val key = Vector3i(v1, v2, v3)
                if (key !in actualCorners) {
                    if (point.x in 0f..size.x && point.y in 0f..size.y) {
                        failed++

                        val cells = ArrayList<Int>()
                        expected.corners.cells.forEach(corner) { cell -> cells.add(cell) }
                        cells.sort()

                        println("Missing corner #$corner, $cells, (${(point.x * grid.numCellsX)},${point.y * grid.numCellsY})")
                    }
                } else {
                    // todo compare edges and cells
                }
            }
        }

        assertEquals(0, failed, "Failures!")

    }


}