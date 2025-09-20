package amitp.mapgen2.graphbuilder.regular

import org.joml.Vector2f
import org.joml.Vector2i
import kotlin.math.sqrt

class RectangleGridBuilder : RegularGridBuilder() {

    companion object {
        private val neighbors = arrayOf(
            Vector2i(-1, -1),
            Vector2i(-1, +0),
            Vector2i(-1, +1),
            Vector2i(+0, +1),
            Vector2i(+1, +1),
            Vector2i(+1, +0),
            Vector2i(+1, -1),
            Vector2i(+0, -1)
        )
    }

    override fun chooseDimensions(aspect: Float, numCells: Int): Vector2i {
        val numCellsX = sqrt(numCells * aspect).toInt()
        val numCellsY = sqrt(numCells / aspect).toInt()
        return Vector2i(numCellsX, numCellsY)
    }

    override fun calculateNumberOfCells(dimensions: Vector2i): Int {
        return dimensions.x * dimensions.y
    }

    override fun getPoint(dimensions: Vector2i, i: Int, j: Int, dst: Vector2f): Vector2f {
        return dst.set(i.toFloat(), j.toFloat())
    }

    override fun numNeighbors(dimensions: Vector2i, i: Int, j: Int): Int = 8

    override fun getNeighborDelta(dimensions: Vector2i, i: Int, j: Int, linkIndex: Int, dst: Vector2i): Vector2i? {
        return neighbors[linkIndex]
    }

}