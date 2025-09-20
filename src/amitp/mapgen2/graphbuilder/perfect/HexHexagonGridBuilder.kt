package amitp.mapgen2.graphbuilder.perfect

import org.joml.Vector2f
import org.joml.Vector2i
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * Builds a graph in the large shape of a hexagon, with many hexagons inside
 * */
class HexHexagonGridBuilder : RectHexagonGridBuilder() {

    override fun chooseDimensions(aspect: Float, numCells: Int): Vector2i {
        // the following algorithm is rounding (yes!, by flooring) to the ideal size
        val s = max(0, sqrt((numCells - 0.25f) / 3f).toInt())
        return Vector2i(2 * s + 1)
    }

    override fun getCellPosOrNull(dimensions: Vector2i, i: Int, j: Int, dst: Vector2f): Vector2f? {
        val s = dimensions.x shr 1
        val jj = j - i.shr(1) + s.shr(1)
        val kk = i + jj - s
        val s2 = s * 2
        return if (i in 0..s2 && jj in 0..s2 && kk in 0..s2) {
            super.getCellPosOrNull(dimensions, i, j, dst)
        } else null
    }

}