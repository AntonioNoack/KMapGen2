package amitp.mapgen2.graphbuilder

import amitp.mapgen2.GeneratedMap
import amitp.mapgen2.graphbuilder.Voronoi.Triangle.Companion.circumcell
import amitp.mapgen2.structures.CellList
import amitp.mapgen2.structures.CornerList
import amitp.mapgen2.structures.EdgeList
import me.anno.maths.Maths.clamp
import me.anno.maths.Maths.median
import me.anno.maths.Packing.pack64
import me.anno.utils.Clock
import me.anno.utils.OS.home
import me.anno.utils.algorithms.ForLoop.forLoopSafely
import me.anno.utils.assertions.assertTrue
import me.anno.utils.structures.arrays.IntArrayList
import org.apache.logging.log4j.LogManager
import org.joml.AABBf
import org.joml.Vector2f
import speiger.primitivecollections.LongToIntHashMap
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO
import kotlin.math.*
import kotlin.random.Random

// todo this doesn't work perfectly yet
class GridVoronoi(points: FloatArray, size: Float) {

    companion object {
        private val LOGGER = LogManager.getLogger(GridVoronoi::class)
    }

    private val numPoints = points.size shr 1
    val clock = Clock(LOGGER)

    private fun findBounds(points: FloatArray): AABBf {
        val bounds = AABBf()
        forLoopSafely(points.size, 2) {
            bounds.union(points[it], points[it + 1], 0f)
        }
        bounds.addMargin(
            bounds.deltaX * 0.5f / numPointsX,
            bounds.deltaY * 0.5f / numPointsY, 0f
        )
        clock.stop("Bounds")
        return bounds
    }

    private val numPointsX = sqrt(numPoints * 10f).toInt()
    private val numPointsY = (numPoints * 10) / numPointsX
    private val gridSize = numPointsX * numPointsY

    private val bounds = findBounds(points)

    private val distancesSq = FloatArray(gridSize)
    private val closest = IntArray(gridSize)

    init {
        distancesSq.fill(Float.POSITIVE_INFINITY)
        closest.fill(-1)
    }

    private val gridX = defineGrid(numPointsX, bounds.minX, bounds.maxX)
    private val gridY = defineGrid(numPointsY, bounds.minY, bounds.maxY)

    init {
        insertInitialDistances(points)
        debugShowIndices()
    }

    val map = findEdges(points, size)

    private fun debugShowIndices() {
        val image = BufferedImage(numPointsX, numPointsY, 1)
        for (y in 0 until numPointsY) {
            for (x in 0 until numPointsX) {
                image.setRGB(x, y, Random(closest[x + y * numPointsX].toLong()).nextInt())
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
        val invX = numPointsX / max(bounds.deltaX, 1e-38f)
        val invY = numPointsY / max(bounds.deltaY, 1e-38f)

        println("min,inv: $minX,$minY,$invX,$invY")
        val maxMinDist = 1f / (invX * invX) + 1f / (invY * invY)

        val radius = 5
        forLoopSafely(points.size, 2) { pi ->
            val px = points[pi]
            val py = points[pi + 1]

            val gx = ((px - minX) * invX).toInt()
            val gy = ((py - minY) * invY).toInt()

            val minXi = max(0, gx - radius)
            val maxXi = min(gx + radius, numPointsX - 1)

            val minYi = max(0, gy - radius)
            val maxYi = min(gy + radius, numPointsY - 1)

            var added = false
            var minDist1 = Float.POSITIVE_INFINITY
            for (yi in minYi..maxYi) {
                for (xi in minXi..maxXi) {
                    val dx = gridX[xi] - px
                    val dy = gridY[yi] - py
                    val distSq = dx * dx + dy * dy
                    val cellIndex = xi + yi * numPointsX
                    minDist1 = min(minDist1, distSq)
                    if (distSq < distancesSq[cellIndex]) {
                        distancesSq[cellIndex] = distSq
                        closest[cellIndex] = pi shr 1
                        added = true
                    }
                }
            }

            assertTrue(minDist1 < maxMinDist) {
                "Illegal min distance[$pi]: $minDist1 for $px,$py -> $gx,$gy -> ${gridX[gx] - px},${gridY[gy] - py}"
            }

            if (!added) {
                val cellIndex = gx + gy * numPointsX
                val prev = closest[cellIndex]
                val prevDist = distancesSq[cellIndex]
                closest[cellIndex] = pi shr 1
                distancesSq[cellIndex] = 0f
                LOGGER.warn("Missed point $pi @$gx,$gy, replacing $prev($prevDist)")
            }
        }

        clock.stop("Initial Distances")

    }

    private fun key(a: Int, b: Int): Long {
        return pack64(min(a, b), max(a, b))
    }

    data class CornersAndEdges(
        val numCorners: Int,
        val cornerData: IntArrayList,
        val numEdges: Int
    )

    private fun findCornersAndEdges(points: FloatArray): CornersAndEdges {
        val cornerData = IntArrayList(numPoints * 4)
        val edges = HashSet<Long>(numPoints * 4) // todo bug: LongHashSet is 10x slower than HashSet<Long>

        var numCorners = 0
        val cornerGrid = IntArray(gridSize)
        cornerGrid.fill(-1)

        val tmp = Vector2f()

        val minX = bounds.minX
        val minY = bounds.minY
        val invX = numPointsX / max(bounds.deltaX, 1e-38f)
        val invY = numPointsY / max(bounds.deltaY, 1e-38f)

        fun onCornerMinMaxMedian(min: Int, median: Int, max: Int) {
            if (min == 50109) {
                println("adding($min,$median,$max)")
            }

            val (px, py) = circumcell(
                points[min * 2], points[min * 2 + 1],
                points[median * 2], points[median * 2 + 1],
                points[max * 2], points[max * 2 + 1], tmp
            )

            val gx = (px - minX) * invX
            val gy = (py - minY) * invY

            // todo check the surrounding, not just one cell
            val gxi = clamp(round(gx).toInt(), 0, numPointsX - 1)
            val gyi = clamp(round(gy).toInt(), 0, numPointsY - 1)
            val cellIndex = gxi + gyi * numPointsX

            var cornerIndex = cornerGrid[cellIndex]
            if (cornerIndex < 0) {
                cornerIndex = numCorners++
                cornerGrid[cellIndex] = cornerIndex
            }

            cornerData.add(min, median, max)
            cornerData.add(cornerIndex, px.toRawBits(), py.toRawBits())

            edges.add(key(min, median))
            edges.add(key(median, max))
            edges.add(key(max, min))
        }

        fun onCorner(ai: Int, bi: Int, ci: Int) {
            if (ai == bi || ai == ci || bi == ci) return
            if (ai == -1 || bi == -1 || ci == -1) return

            val min = min(ai, min(bi, ci))
            val max = max(ai, max(bi, ci))
            val median = median(ai, bi, ci)
            onCornerMinMaxMedian(min, median, max)
        }

        val unique = IntArrayList()

        for (yi in 1 until numPointsY) {
            for (xi in 1 until numPointsX) {
                val cellIndex = xi + yi * numPointsX
                val ai = closest[cellIndex]
                val bi = closest[cellIndex - 1]
                val ci = closest[cellIndex - numPointsX]
                val di = closest[cellIndex - numPointsX - 1]

                if (ai == di || bi == ci) continue

                if (ai != -1) unique.add(ai)
                if (bi != -1 && bi !in unique) unique.add(bi)
                if (ci != -1 && ci !in unique) unique.add(ci)
                if (di != -1 && di !in unique) unique.add(di)

                if (unique.size > 2) {
                    if (abs(gridX[xi] - 2009) < 20f &&
                        abs(gridY[yi] - 2304) < 20f
                    ) {
                        println("Corner[#${cornerData.size / 3},$xi,$yi] -> $unique")
                    }
                }

                when (unique.size) {
                    3 -> {
                        val i0 = unique[0]
                        val i1 = unique[1]
                        val i2 = unique[2]
                        val min = min(i0, min(i1, i2))
                        val max = max(i0, max(i1, i2))
                        val median = median(i0, i1, i2)
                        onCornerMinMaxMedian(min, median, max)
                    }
                    4 -> {
                        onCorner(ai, bi, ci)
                        onCorner(ai, bi, di)
                        onCorner(bi, di, ci)
                        onCorner(ai, ci, di)
                    }
                }
                unique.clear()
            }
        }

        clock.stop("Corners & Unique Edges")
        return CornersAndEdges(numCorners, cornerData, edges.size)
    }

    private fun findEdges(points: FloatArray, size: Float): GeneratedMap {

        val cells = CellList(numPoints)
        cells.setPoints(points)

        val (numCorners, cornerData, numEdges) = findCornersAndEdges(points)
        val corners = CornerList(numCorners)

        setCornerPositions(corners, cornerData)
        clock.stop("Corner Positions")

        val edges = cornerEdges(numEdges, cells, corners, cornerData)
        clock.stop("Corner Edges")

        cornerVertices(cells, corners, cornerData)
        clock.stop("Corner Vertices")

        cornerNeighbors(edges, corners)
        clock.stop("Corner-Neighbors")

        return GeneratedMap(cells, corners, edges, size)
    }

    private fun setCornerPositions(corners: CornerList, cornerData: IntArrayList) {
        forLoopSafely(cornerData.size, 6) { i ->
            val corner = cornerData[i + 3]
            val px = Float.fromBits(cornerData[i + 4])
            val py = Float.fromBits(cornerData[i + 5])
            corners.setPoint(corner, px, py)
        }
    }

    private fun cornerNeighbors(edges: EdgeList, corners: CornerList) {
        for (c in 0 until edges.size) {
            val corner1 = edges.getCornerA(c)
            val corner2 = edges.getCornerB(c)
            if (corner1 >= 0 && corner2 >= 0 && corner1 != corner2) {
                corners.neighbors.addUnique(corner1, corner2)
                corners.neighbors.addUnique(corner2, corner1)
            }
        }
    }

    private fun cornerEdges(
        numEdges: Int, cells: CellList,
        corners: CornerList, cornerData: IntArrayList
    ): EdgeList {
        val doneEdges = LongToIntHashMap(-1)
        val edges = EdgeList(numEdges)
        var edge = 0

        fun addEdge(ai: Int, bi: Int, cornerIndex: Int) {

            val key = key(ai, bi)
            val prevCorner = doneEdges.put(key, edge) // overriding is fine, because we won't do it again
            if (prevCorner == -1) {

                cells.neighbors.addUnique(ai, bi)
                cells.neighbors.addUnique(bi, ai)

                edges.setCellA(edge, ai)
                edges.setCellB(edge, bi)
                edges.setCornerA(edge, cornerIndex)
                edges.setCornerB(edge, -1)

                cells.edges.add(ai, edge)
                cells.edges.add(bi, edge)
                corners.edges.add(cornerIndex, edge)

                edge++
            } else {
                edges.setCornerB(prevCorner, cornerIndex)
            }
        }

        forLoopSafely(cornerData.size, 6) { i ->

            val ai = cornerData[i]
            val bi = cornerData[i + 1]
            val ci = cornerData[i + 2]

            val corner = cornerData[i + 3]
            addEdge(ai, bi, corner)
            addEdge(bi, ci, corner)
            addEdge(ci, ai, corner)
        }

        return edges
    }

    private fun cornerVertices(
        cells: CellList, corners: CornerList,
        cornerData: IntArrayList
    ) {

        fun addVertex(ai: Int, corner: Int) {
            corners.cells.add(corner, ai)
            cells.corners.add(ai, corner)
        }

        forLoopSafely(cornerData.size, 6) { i ->
            val ai = cornerData[i]
            val bi = cornerData[i + 1]
            val ci = cornerData[i + 2]

            val corner = cornerData[i + 3]
            addVertex(ai, corner)
            addVertex(bi, corner)
            addVertex(ci, corner)
        }
    }
}