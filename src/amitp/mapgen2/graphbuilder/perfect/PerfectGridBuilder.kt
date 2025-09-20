package amitp.mapgen2.graphbuilder.perfect

import amitp.mapgen2.GeneratedMap
import amitp.mapgen2.graphbuilder.GraphBuilder
import amitp.mapgen2.pointselector.PointSelector
import amitp.mapgen2.structures.*
import me.anno.maths.Maths.posMod
import me.anno.maths.Packing
import me.anno.utils.structures.arrays.FloatArrayList
import me.anno.utils.structures.arrays.FloatArrayListUtils.add
import org.joml.AABBf
import org.joml.Vector2f
import org.joml.Vector2i
import speiger.primitivecollections.LongToIntHashMap
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Builds a non-randomized grid really quickly
 */
abstract class PerfectGridBuilder : GraphBuilder, PointSelector {

    companion object {
        fun getScale(size: Vector2f, bounds: AABBf): Float {
            return min(size.x / bounds.deltaX, size.y / bounds.deltaY)
        }

        fun getOffset(size: Vector2f, bounds: AABBf): Vector2f {
            val scale = getScale(size, bounds)
            return Vector2f(-bounds.centerX * scale + size.x * 0.5f, -bounds.centerY * scale + size.y * 0.5f)
        }

        fun transform(points: PointList, scale: Float, offset: Vector2f) {
            for (i in points.indices) {
                val x = points.getPointX(i) * scale + offset.x
                val y = points.getPointY(i) * scale + offset.y
                points.setPoint(i, x, y)
            }
        }


        fun transform(points: FloatArray, scale: Float, offset: Vector2f) {
            for (i in 0 until points.size.shr(1)) {
                points[i * 2] = points[i * 2] * scale + offset.x
                points[i * 2 + 1] = points[i * 2 + 1] * scale + offset.y
            }
        }
    }

    abstract fun getCellPosOrNull(dimensions: Vector2i, i: Int, j: Int, dst: Vector2f): Vector2f?
    abstract fun getCornerPositionAndKey(i: Int, j: Int, cornerId: Int, dst: Vector2f): Long

    abstract fun numCornersPerCell(): Int
    abstract fun numEdgesPerCell(): Int

    abstract fun numCellNeighbors(): Int
    abstract fun numCornerNeighbors(): Int

    open fun chooseDimensions(aspect: Float, numCells: Int): Vector2i {
        val cornerBounds = AABBf()
        val tmp = Vector2f()
        for (i in 0 until numCornersPerCell()) {
            getCornerPositionAndKey(0, 0, i, tmp)
            cornerBounds.union(tmp)
        }

        if (cornerBounds.isEmpty()) {
            val sqrt = sqrt(numCells.toFloat()).toInt()
            return Vector2i(sqrt, sqrt)
        }

        val aspect = cornerBounds.deltaX / cornerBounds.deltaY

        val numCellsX = sqrt(numCells * aspect).toInt()
        val numCellsY = sqrt(numCells / aspect).toInt()
        return Vector2i(numCellsX, numCellsY)
    }

    open fun estimateNumberOfCorners(dimensions: Vector2i, numCells: Int): Int {
        val innerCorners = numCornersPerCell() * numCells * 2 / numCornerNeighbors()
        val cornersForEdges = numCornersPerCell() * (dimensions.x + dimensions.y)
        return innerCorners + cornersForEdges
    }

    open fun estimateNumberOfEdges(dimensions: Vector2i, numCells: Int): Int {
        return estimateNumberOfCorners(dimensions, numCells) * numEdgesPerCell() / numCornersPerCell()
    }

    private fun createCells(numCells: Int, dimensions: Vector2i): CellList {
        val cells = CellList(numCells)
        val tmp = Vector2f()
        var k = 0
        for (y in 0 until dimensions.y) {
            for (x in 0 until dimensions.x) {
                if (getCellPosOrNull(dimensions, x, y, tmp) != null) {
                    cells.setPoint(k++, tmp.x, tmp.y)
                }
            }
        }
        cells.resizeTo(k)
        return cells
    }

    private fun fillCornersAndEdges(
        numCells: Int, dimensions: Vector2i,
        cells: CellList, size: Vector2f
    ): GeneratedMap {

        val cornerIdToIndex = LongToIntHashMap(-1, numCells * 2)
        val corners = CornerList(estimateNumberOfCorners(dimensions, numCells))

        val edgeIdToIndex = LongToIntHashMap(-1, numCells * 2)
        val edges = EdgeList(estimateNumberOfEdges(dimensions, numCells))
        edges.fillEmpty()

        val tmp = Vector2f()
        val bounds = AABBf()
        for (i in cells.indices) {
            tmp.set(cells.getPointX(i), cells.getPointY(i))
            bounds.union(tmp)
        }

        val numCorners = numCornersPerCell()
        val cornerList = IntArray(numCorners)
        var cell = 0
        for (j in 0 until dimensions.y) {
            for (i in 0 until dimensions.x) {
                if (getCellPosOrNull(dimensions, i, j, tmp) == null) continue

                for (k in 0 until numCorners) {
                    val key = getCornerPositionAndKey(i, j, k, tmp)
                    val corner = cornerIdToIndex.getOrPut(key) { cornerIdToIndex.size }
                    corners.setPoint(corner, tmp.x, tmp.y)
                    bounds.union(tmp)

                    corners.cells.add(corner, cell)
                    cells.corners.add(cell, corner)
                    cornerList[k] = corner
                }

                // create edges between corners
                for (i in 0 until numCorners) {
                    val c0 = cornerList[i]
                    val c1 = cornerList[posMod(i + 1, numCorners)]
                    corners.neighbors.addUnique(c0, c1)
                    corners.neighbors.addUnique(c1, c0)

                    val edgeId = Packing.pack64(min(c0, c1), max(c0, c1))
                    var new = false
                    val edge = edgeIdToIndex.getOrPut(edgeId) {
                        new = true
                        edgeIdToIndex.size
                    }
                    if (new) {

                        edges.setCornerA(edge, c0)
                        edges.setCornerB(edge, c1)

                        corners.edges.addUnique(c0, edge)
                        corners.edges.addUnique(c1, edge)

                        edges.setCellA(edge, cell)

                    } else {
                        edges.setCellB(edge, cell)

                        val otherCell = edges.getCellA(edge)
                        cells.neighbors.add(cell, otherCell)
                        cells.neighbors.add(otherCell, cell)
                    }
                }

                cell++
            }
        }

        corners.resizeTo(cornerIdToIndex.size)
        edges.resize(edgeIdToIndex.size)

        val scale = getScale(size, bounds)
        val offset = getOffset(size, bounds)

        transform(cells, scale, offset)
        transform(corners, scale, offset)

        setBorder(cells, cells.neighbors, numCellNeighbors())
        setBorder(corners, corners.neighbors, numCornerNeighbors())

        return GeneratedMap(cells, corners, edges, size)
    }

    private fun setBorder(cells: PointList, neighbors: PackedIntLists, numExpected: Int) {
        for (cell in cells.indices) {
            cells.setBorder(cell, neighbors.getSize(cell) < numExpected)
        }
    }

    override fun buildGraph(size: Vector2f, numCells: Int, seed: Long): GeneratedMap {
        val dimensions = chooseDimensions(size.x / size.y, numCells)
        val numCells = dimensions.x * dimensions.y
        val cells = createCells(numCells, dimensions)
        return fillCornersAndEdges(numCells, dimensions, cells, size)
    }

    override fun select(size: Vector2f, numCells: Int, seed: Long): FloatArray {
        val dimensions = chooseDimensions(size.x / size.y, numCells)
        val cells = FloatArrayList(numCells * 2)
        val bounds = AABBf()
        val tmp = Vector2f()
        val numCorners = numCornersPerCell()
        for (j in 0 until dimensions.y) {
            for (i in 0 until dimensions.x) {
                if (getCellPosOrNull(dimensions, i, j, tmp) != null) {
                    cells.add(tmp.x, tmp.y)
                    bounds.union(tmp)

                    // include corners in bounds
                    for (k in 0 until numCorners) {
                        getCornerPositionAndKey(i, j, k, tmp)
                        bounds.union(tmp)
                    }
                }
            }
        }

        val scale = getScale(size, bounds)
        val offset = getOffset(size, bounds)
        val cellsI = cells.toFloatArray(true)
        transform(cellsI, scale, offset)

        return cellsI
    }
}