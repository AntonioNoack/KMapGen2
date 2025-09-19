package amitp.mapgen2.graphbuilder

import amitp.mapgen2.GeneratedMap
import amitp.mapgen2.graphbuilder.Voronoi.Triangle.Companion.circumcenter
import me.anno.utils.assertions.assertEquals
import me.anno.utils.structures.arrays.FloatArrayList
import me.anno.utils.structures.arrays.FloatArrayListUtils.add
import me.anno.utils.structures.arrays.IntArrayList
import org.joml.AABBf
import org.joml.Vector2f
import org.joml.Vector2i
import kotlin.math.min

// todo define a regular grid by defining nodes and neighbors,
//  and then using N levels of neighbors, and maybe some randomized variation,
//  generate a regular voronoi-cell grid from it
abstract class RegularGridBuilder : GraphBuilder {

    override fun buildGraph(size: Vector2f, numCells: Int, seed: Long): GeneratedMap {
        val dimensions = chooseDimensions(size.x / size.y, numCells)

        val gridToCellIndex = IntArray(dimensions.x * dimensions.y * 2)
        gridToCellIndex.fill(-1)

        val totalNumCells = calculateNumberOfCells(dimensions)
        val cellPositions = FloatArray(totalNumCells * 2)

        var cellIndex = 0
        val bounds = AABBf()
        val pt = Vector2f()
        for (yi in 0 until dimensions.y) {
            for (xi in 0 until dimensions.x) {
                if (getPoint(dimensions, xi, yi, pt) != null) {
                    cellPositions[cellIndex * 2] = pt.x
                    cellPositions[cellIndex * 2 + 1] = pt.y
                    bounds.union(pt)
                    gridToCellIndex[xi + yi * dimensions.x] = cellIndex
                    cellIndex++
                }
            }
        }

        assertEquals(cellIndex, totalNumCells)

        val scale = min(size.x / bounds.deltaX, size.y / bounds.deltaY)
        val offset = Vector2f(-bounds.centerX / scale + size.x * 0.5f, -bounds.centerY / scale + size.y * 0.5f)

        for (i in 0 until totalNumCells) {
            cellPositions[i * 2] = cellPositions[i * 2] * scale + offset.x
            cellPositions[i * 2 + 1] = cellPositions[i * 2 + 1] * scale + offset.y
        }

        val tmpI = Vector2i()
        val neighbors = IntArrayList(16)
        val corners = FloatArrayList(16)

        for (yi in 0 until dimensions.y) {
            for (xi in 0 until dimensions.x) {
                val cellIndex = gridToCellIndex[xi + yi * dimensions.x]
                if (cellIndex < 0) continue
                val numNeighbors = numNeighbors(dimensions, xi, yi)
                for (ni in 0 until numNeighbors) {
                    val neighbor = getNeighborDelta(dimensions, xi, yi, ni, tmpI)
                        ?: continue
                    val xni = xi + neighbor.x
                    val yni = yi + neighbor.y
                    if (xni in 0 until dimensions.x && yni in 0 until dimensions.y) {
                        val cell = gridToCellIndex[xni + yni * dimensions.x]
                        if (cell < 0) continue
                        neighbors.add(cell)
                    }
                }

                for (i in 1 until neighbors.size) {
                    search@ for (j in 0 until i) {
                        val ni = neighbors[i]
                        val nj = neighbors[j]

                        val (cx, cy) = circumcenter(
                            cellPositions[cellIndex * 2],
                            cellPositions[cellIndex * 2 + 1],
                            cellPositions[ni * 2],
                            cellPositions[ni * 2 + 1],
                            cellPositions[nj * 2],
                            cellPositions[nj * 2 + 1]
                        )

                        val dx = cellPositions[cellIndex * 2] - cx
                        val dy = cellPositions[cellIndex * 2 + 1] - cy
                        val radiusSq = dx * dx + dy * dy

                        for (k in neighbors.indices) {
                            if (k == i || k == j) continue

                            val neighborIndex = neighbors[k]
                            val dx = cellPositions[neighborIndex * 2] - cx
                            val dy = cellPositions[neighborIndex * 2 + 1] - cy
                            if (dx * dx + dy * dy <= radiusSq) {
                                continue@search
                            }
                        }

                        // found a valid corner
                        corners.add(cx, cy)

                    }
                }

                // for every cell in the grid,
                //  iterate all neighbors,
                //  and for all of them, find the valid voronoi cells
                //  (aka for any triple (self, neighbor0, neighbor1) there is no cell inside that circumcircle)

                // todo given corners, find the edges by sorting the corners by their angle,
                //  and finally insert all them into some kind of data structure...
                //  also uniquely identify corners somehow...

                neighbors.clear()
                corners.clear()
            }
        }

        TODO("Not yet implemented")
    }

    abstract fun chooseDimensions(aspect: Float, numCells: Int): Vector2i
    abstract fun calculateNumberOfCells(dimensions: Vector2i): Int

    abstract fun getPoint(dimensions: Vector2i, i: Int, j: Int, dst: Vector2f): Vector2f?

    abstract fun numNeighbors(dimensions: Vector2i, i: Int, j: Int): Int
    abstract fun getNeighborDelta(dimensions: Vector2i, i: Int, j: Int, linkIndex: Int, dst: Vector2i): Vector2i?
}