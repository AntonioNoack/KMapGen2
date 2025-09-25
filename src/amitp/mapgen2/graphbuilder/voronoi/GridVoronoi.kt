package amitp.mapgen2.graphbuilder.voronoi

import amitp.mapgen2.GeneratedMap
import amitp.mapgen2.structures.CellList
import amitp.mapgen2.structures.CornerList
import amitp.mapgen2.structures.EdgeList
import me.anno.maths.Maths.sq
import me.anno.maths.MinMax.max
import me.anno.maths.MinMax.min
import me.anno.maths.paths.PathFinding
import me.anno.utils.Clock
import me.anno.utils.OS.home
import me.anno.utils.algorithms.ForLoop.forLoopSafely
import me.anno.utils.assertions.assertEquals
import me.anno.utils.assertions.assertTrue
import me.anno.utils.structures.arrays.IntArrayList
import org.apache.logging.log4j.LogManager
import org.joml.AABBf
import org.joml.Vector2f
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * Should be faster than VoronoiGraphBuilder, but may introduce shall errors,
 * and will never be as fast as PerfectGridBuilder
 * */
class GridVoronoi(points: FloatArray, val size: Vector2f) {

    companion object {
        private val LOGGER = LogManager.getLogger(GridVoronoi::class)
    }

    private val numCells = points.size shr 1
    val clock = Clock(LOGGER)

    private fun findBounds(points: FloatArray): AABBf {
        val bounds = AABBf()
        forLoopSafely(points.size, 2) {
            bounds.union(points[it], points[it + 1], 0f)
        }
        val extra = 0.5f
        bounds.addMargin(
            bounds.deltaX * extra / numCellsX,
            bounds.deltaY * extra / numCellsY, 0f
        )
        clock.stop("Bounds")
        return bounds
    }

    val maxRadius = 10
    val scale = 20 // at max (maxRadius/4 + 1.5)²

    val numCellsX = max(sqrt(numCells * scale.toFloat()).toInt(), 3)
    val numCellsY = max((numCells * scale) / numCellsX, 3)
    private val gridSize = numCellsX * numCellsY

    private val bounds = findBounds(points)

    private val distancesSq = FloatArray(gridSize)
    private val closest = IntArray(gridSize)

    init {
        distancesSq.fill(Float.POSITIVE_INFINITY)
        closest.fill(-1)
    }

    private val gridX = defineGrid(numCellsX, bounds.minX, bounds.maxX)
    private val gridY = defineGrid(numCellsY, bounds.minY, bounds.maxY)

    init {
        insertInitialDistances(points)
        debugShowIndices()
    }

    val map = findEdges(points, size)

    private fun debugShowIndices() {
        val image = BufferedImage(numCellsX, numCellsY, 1)
        for (y in 0 until numCellsY) {
            for (x in 0 until numCellsX) {
                val id = closest[x + y * numCellsX]
                val color = if (false) Random(id.toLong()).nextInt() else
                    Random(id.toLong()).nextInt().and(0xffff00) or id.and(255)
                image.setRGB(x, y, color)
            }
        }
        ImageIO.write(image, "png", File(home.getChild("Desktop/indices.png").absolutePath))
    }

    private fun defineGrid(size: Int, min: Float, max: Float): FloatArray {
        val invX = size / max(max - min, 1e-38f)
        val grid = FloatArray(size)
        for (i in grid.indices) {
            grid[i] = min + (i + 0.5f) / invX
        }
        return grid
    }

    private fun insertInitialDistances(points: FloatArray) {

        val minX = bounds.minX
        val minY = bounds.minY
        val invX = numCellsX / max(bounds.deltaX, 1e-38f)
        val invY = numCellsY / max(bounds.deltaY, 1e-38f)

        LOGGER.info("Min,Inv: $minX,$minY, $invX,$invY")
        val maxMinDist = 1f / (invX * invX) + 1f / (invY * invY)

        forLoopSafely(points.size, 2) { pi ->
            val px = points[pi]
            val py = points[pi + 1]

            val gx = ((px - minX) * invX).toInt()
            val gy = ((py - minY) * invY).toInt()

            var added = false
            var minDist1 = Float.POSITIVE_INFINITY
            fun checkAdd(dxi: Int, dyi: Int) {
                val xi = gx + dxi
                val yi = gy + dyi
                if (xi !in 0 until numCellsX || yi !in 0 until numCellsY) return
                val dx = gridX[xi] - px
                val dy = gridY[yi] - py
                val distSq = dx * dx + dy * dy
                val cellIndex = xi + yi * numCellsX
                minDist1 = min(minDist1, distSq)
                if (distSq < distancesSq[cellIndex]) {
                    distancesSq[cellIndex] = distSq
                    closest[cellIndex] = pi shr 1
                    added = true
                }
            }

            checkAdd(0, 0)
            var anyAdded = false
            for (radius in 1 until maxRadius) {
                added = false
                for (j in -radius until radius) {
                    checkAdd(+radius, +j)
                    checkAdd(-radius, -j)
                    checkAdd(-j, +radius)
                    checkAdd(+j, -radius)
                }
                if (radius > 3 && !added) break
                if (added) anyAdded = true
            }

            assertTrue(minDist1 < maxMinDist) {
                "Illegal min distance[$pi]: $minDist1 for $px,$py -> $gx,$gy -> ${gridX[gx] - px},${gridY[gy] - py}"
            }

            if (!anyAdded) {
                val cellIndex = gx + gy * numCellsX
                val prev = closest[cellIndex]
                val prevDist = distancesSq[cellIndex]
                closest[cellIndex] = pi shr 1
                distancesSq[cellIndex] = 0f
                LOGGER.warn("Missed point $pi @$gx,$gy, replacing $prev($prevDist)")
            }
        }

        clock.stop("Initial Distances")

    }

    private fun findCornersAndEdges(points: FloatArray): CircleGrid {
        val circleGrid = CircleGrid(numCellsX, numCellsY, bounds)

        forLoopSafely(points.size, 2) { i ->
            circleGrid.addCell(points[i], points[i + 1])
        }
        clock.stop("Fill Circles")

        val tmp = Vector2f()
        fun onCorner(ai: Int, bi: Int, ci: Int) {
            val corner = Voronoi.Triangle.circumcenter(
                points[ai * 2], points[ai * 2 + 1],
                points[bi * 2], points[bi * 2 + 1],
                points[ci * 2], points[ci * 2 + 1], tmp
            ) ?: return
            val (px, py) = corner

            if (px <= 0f || px >= size.x || py <= 0f || py >= size.y) return // out of bounds -> will be handled later

            val radiusSq = sq(points[ai * 2] - px) + sq(points[ai * 2 + 1] - py)
            if (radiusSq.isFinite()) {
                circleGrid.addCircumcircle(px, py, radiusSq, ai, bi, ci)
            } // else covered anyway
        }

        // iterate over center
        val unique = IntArrayList()
        for (yi in 0 until numCellsY - 2) {
            for (xi in 0 until numCellsX - 2) {
                val cellIndex = xi + yi * numCellsX
                for (dy in 0 until 3) {
                    for (dx in 0 until 3) {
                        val ai = closest[cellIndex + dx + dy * numCellsX]
                        if (ai != -1 && ai !in unique) unique.add(ai)
                    }
                }

                for (i in 2 until unique.size) {
                    for (j in 1 until i) {
                        for (k in 0 until j) {
                            val i0 = unique[i]
                            val i1 = unique[j]
                            val i2 = unique[k]
                            // println("u3: ${min(i0, i1, i2)},${median(i0, i1, i2)},${max(i0, i1, i2)}")
                            onCorner(i0, i1, i2)
                        }
                    }
                }
                unique.clear()
            }
        }
        clock.stop("Find Corners in Center")
        return circleGrid
    }

    private fun findEdges(points: FloatArray, size: Vector2f): GeneratedMap {

        val cells = CellList(numCells)
        cells.setPoints(points)

        val circleGrid = findCornersAndEdges(points)
        val (corners, edges) = circleGrid.extractCornersAndEdges(cells, clock)

        findCornersOnEdges(cells, corners, edges)
        clock.stop("Find Corners on Edge")

        cornerNeighbors(edges, corners)
        clock.stop("Corner-Neighbors")

        return GeneratedMap(cells, corners, edges, size)
    }

    class ToBorder(
        val cellA: Int,
        val cellB: Int,
        val cornerA: Int,
        val posX: Float,
        val posY: Float,
    )

    private fun findEdgesToBorder(cells: CellList, corners: CornerList): List<ToBorder> {
        val edgesToBorder = ArrayList<ToBorder>()

        // iterate over border (without max, this would be an expensive O(n²) search, where n ~ numCellsX)
        var cellA = -1

        fun connect(cellA: Int, cellB: Int): Boolean {
            // find the common corner between cell ai and bi:
            //  there should only be one

            val aix = cells.getPointX(cellA)
            val aiy = cells.getPointY(cellA)
            val bix = cells.getPointX(cellB)
            val biy = cells.getPointY(cellB)

            val abx = bix - aix
            val aby = biy - aiy

            val cx = (aix + bix) * 0.5f
            val cy = (aiy + biy) * 0.5f

            var bestCorner = -1
            var bestScore = 0f

            fun checkCorner(corner: Int, otherCell: Int) {
                if (corners.cells.contains(corner, otherCell)) {

                    val cnx = corners.getPointX(corner)
                    val cny = corners.getPointY(corner)

                    val dirX = cx - cnx
                    val dirY = cy - cny

                    // shall be perpendicular
                    val score = abs(dirX * aby - dirY * abx) / max(hypot(dirX, dirY), 1e-9f)
                    if (score > bestScore) {
                        @Suppress("AssignedValueIsNeverRead") // Intellij is broken
                        bestScore = score
                        bestCorner = corner
                    }

                }
            }

            cells.corners.forEach(cellA) { corner -> checkCorner(corner, cellB) }
            cells.corners.forEach(cellB) { corner ->
                checkCorner(corner, cellA)
            }

            if (bestCorner >= 0) {
                // calculate the center between ai and bi,
                //  and connect that through sharedCorner to the edge into infinity

                val cnx = corners.getPointX(bestCorner)
                val cny = corners.getPointY(bestCorner)

                var dirX = cx - cnx
                var dirY = cy - cny

                // may be flipped -> flip it back
                if (dirX * (cx - size.x * 0.5f) + dirY * (cy - size.y * 0.5f) < 0f) {
                    dirX = -dirX
                    dirY = -dirY
                }

                // calculate length
                val epsilon = 1e-4f
                val lenX =
                    if (dirX < -epsilon) -cx / dirX
                    else if (dirX > epsilon) (size.x - cx) / dirX
                    else Float.POSITIVE_INFINITY
                val lenY =
                    if (dirY < -epsilon) -cy / dirY
                    else if (dirY > epsilon) (size.y - cy) / dirY
                    else Float.POSITIVE_INFINITY

                // calculate hit on edge
                val hitX = if (lenX < lenY) if (dirX < 0f) 0f else size.x else (cx + dirX * lenY)
                val hitY = if (lenX < lenY) (cy + dirY * lenX) else if (dirY < 0f) 0f else size.y

                // println("edgesToBorder += ($cellA,$cellB)")
                edgesToBorder.add(ToBorder(cellA, cellB, bestCorner, hitX, hitY))

                return true
            } else return false
        }

        fun onBorderPixel(xi: Int, yi: Int) {
            val cellIndex = xi + yi * numCellsX
            val cellB = closest[cellIndex]
            if (cellB == -1) return // or should we keep it??? for now assume a filled border
            if (cellB == cellA) return // already seen

            // we found a new cell on the border
            // println("next border cell: $cellB")

            if (cellA == -1) {
                @Suppress("AssignedValueIsNeverRead") // Intellij is stupid
                cellA = cellB
                return // wait for the next round
            }

            if (!connect(cellA, cellB)) {

                // we must use path-finding to find the correct path

                val neighbors = ArrayList<Int>()
                val path = PathFinding<Int>().aStar(
                    cellA, cellB, { cellA, cellB ->
                        val dx = cells.getPointX(cellB) - cells.getPointX(cellA)
                        val dy = cells.getPointY(cellB) - cells.getPointY(cellA)
                        (dx * dx + dy * dy).toDouble()
                    }, { cell ->
                        neighbors.clear()
                        if (cell >= 0) {
                            cells.neighbors.forEach(cell) { neighborCorner ->
                                neighbors.add(neighborCorner)
                            }
                        }
                        neighbors
                    }, Double.POSITIVE_INFINITY,
                    includeStart = true, includeEnd = true
                )

                if (path != null) {
                    LOGGER.info("Found cell path: $path")
                    for (i in 1 until path.size) {
                        connect(path[i - 1], path[i])
                    }
                } else LOGGER.warn("Could not find path from cell $cellA to cell $cellB")
            }

            @Suppress("AssignedValueIsNeverRead") // Intellij is lying
            cellA = cellB
        }

        for (x in 0 until numCellsX) onBorderPixel(x, 0)
        for (y in 0 until numCellsY) onBorderPixel(numCellsX - 1, y)
        for (x in numCellsX - 1 downTo 0) onBorderPixel(x, numCellsY - 1)
        for (y in numCellsY - 1 downTo 0) onBorderPixel(0, y)

        return edgesToBorder
    }

    private fun findCornersOnEdges(cells: CellList, corners: CornerList, edges: EdgeList) {

        val edgesToBorder = findEdgesToBorder(cells, corners)
        if (edgesToBorder.isEmpty()) return

        LOGGER.info("Adding ${edgesToBorder.size} edges/corners to the border (${corners.size} corners + ${edges.size} edges)")

        var numExtraCorners = edgesToBorder.size
        var numExtraEdges = edgesToBorder.size

        var last = edgesToBorder.lastIndex
        for (i in 0 until edgesToBorder.size) {
            if (i == last) break
            val corner0 = edgesToBorder[last]
            val corner1 = edgesToBorder[i]

            if (corner0.cellB == corner1.cellA) {
                numExtraEdges++

                // calculate whether we need an extra corner vertex
                val needCornerVertex = corner0.posX != corner1.posX && corner0.posY != corner1.posY
                if (needCornerVertex) {
                    numExtraCorners++
                    numExtraEdges++
                }
            }

            last = i
        }

        var edge = edges.size
        var corner = corners.size
        val firstNewCorner = corner

        LOGGER.info("Extra corners: $numExtraCorners, extra edges: $numExtraEdges, edges to border: ${edgesToBorder.size}")

        corners.resizeTo(corners.size + numExtraCorners)
        edges.resize(edges.size + numExtraEdges)

        fun createBorderEdge(
            cornerA: Int, cornerB: Int,
            cellA: Int, cellB: Int,
        ) {

            assertTrue(cornerA in corners.indices)
            assertTrue(cornerB in corners.indices)

            edges.setCornerA(edge, cornerA)
            edges.setCornerB(edge, cornerB)
            edges.setCellA(edge, cellA)
            edges.setCellB(edge, cellB)

            cells.edges.addUnique(cellA, edge)
            corners.edges.addUnique(cornerA, edge)
            corners.edges.addUnique(cornerB, edge)

            if (cellB >= 0) {
                cells.edges.addUnique(cellB, edge)
                cells.neighbors.addUnique(cellA, cellB)
                cells.neighbors.addUnique(cellB, cellA)
            }

            edge++
        }

        fun createBorderCorner(posX: Float, posY: Float, cellA: Int, cellB: Int, edgeA: Int, edgeB: Int) {
            corners.setPoint(corner, posX, posY)
            corners.setBorder(corner, true)

            corners.edges.addUnique(corner, edgeA)
            corners.cells.add(corner, cellA)
            cells.corners.add(cellA, corner)

            // cells.edges.addUnique(cellA, edgeA)

            if (edgeB >= 0) {
                corners.edges.addUnique(corner, edgeB)
                // cells.edges.addUnique(cellA, edgeB)
            }
            if (cellB >= 0) {
                corners.cells.add(corner, cellB)
                cells.corners.add(cellB, corner)
                /*cells.edges.addUnique(cellB, edgeA)
                if (edgeB >= 0{
                    cells.edges.addUnique(cellB, edgeB)
                }*/
            }

            corner++
        }

        for (i in edgesToBorder.indices) {
            val toBorder = edgesToBorder[i]
            val cellA = toBorder.cellA
            val cellB = toBorder.cellB

            val edgeI = edge
            createBorderEdge(toBorder.cornerA, corner, cellA, cellB)
            createBorderCorner(toBorder.posX, toBorder.posY, cellA, cellB, edgeI, -1)
        }

        // connect borders at the top (need to check ai and bi of neighboring results)
        last = edgesToBorder.lastIndex
        for (i in 0 until edgesToBorder.size) {
            if (i == last) break
            val corner0 = edgesToBorder[last]
            val corner1 = edgesToBorder[i]

            if (corner0.cellB == corner1.cellA) {

                // connect these two corners
                val corner0i = firstNewCorner + last
                val corner1i = firstNewCorner + i

                val cell = corner0.cellB

                // calculate whether we need an extra corner vertex
                val needCornerVertex = corner0.posX != corner1.posX && corner0.posY != corner1.posY
                if (needCornerVertex) {

                    val edgeI = edge
                    createBorderEdge(corner0i, corner, cell, -1)
                    createBorderEdge(corner1i, corner, cell, -1)
                    assertEquals(edge, edgeI + 2)

                    // define corner position
                    val posX = if (corner0.posX == 0f || corner1.posX == 0f) 0f else size.x
                    val posY = if (corner0.posY == 0f || corner1.posY == 0f) 0f else size.y
                    createBorderCorner(posX, posY, cell, -1, edgeI, edgeI + 1)

                } else {
                    createBorderEdge(corner0i, corner1i, cell, -1)
                }
            }

            last = i
        }

        assertEquals(corners.size, corner)
        assertEquals(edges.size, edge)

    }

    private fun cornerNeighbors(edges: EdgeList, corners: CornerList) {
        for (c in edges.indices) {
            val corner1 = edges.getCornerA(c)
            val corner2 = edges.getCornerB(c)
            if (corner1 >= 0 && corner2 >= 0 && corner1 != corner2) {
                corners.neighbors.addUnique(corner1, corner2)
                corners.neighbors.addUnique(corner2, corner1)
            }
        }
    }
}