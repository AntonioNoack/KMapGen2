package amitp.mapgen2.graphbuilder

import me.anno.maths.chunks.hexagon.HexagonGridMaths
import me.anno.maths.chunks.hexagon.HexagonGridMaths.di
import me.anno.maths.chunks.hexagon.HexagonGridMaths.dj
import org.joml.Vector2f
import org.joml.Vector2i
import kotlin.math.sqrt

class HexagonGridBuilder : RegularGridBuilder() {

    // todo option to use honey-comb shape instead of rectangle

    override fun chooseDimensions(aspect: Float, numCells: Int): Vector2i {
        val sqrt = sqrt(numCells.toFloat()).toInt()
        return Vector2i(sqrt, sqrt) // not ideal...
    }

    override fun calculateNumberOfCells(dimensions: Vector2i): Int {
        return dimensions.x * dimensions.y
    }

    override fun getPoint(dimensions: Vector2i, i: Int, j: Int, dst: Vector2f): Vector2f? {
        val j = j - i.shr(1)
        return dst.set(di.x * i + dj.x * j, di.y * i + dj.y * j)
    }

    override fun numNeighbors(dimensions: Vector2i, i: Int, j: Int): Int = 6

    override fun getNeighborDelta(dimensions: Vector2i, i: Int, j: Int, linkIndex: Int, dst: Vector2i): Vector2i? {
        return HexagonGridMaths.neighbors[linkIndex]
    }

}