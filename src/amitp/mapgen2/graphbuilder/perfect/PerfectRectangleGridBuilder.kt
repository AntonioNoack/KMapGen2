package amitp.mapgen2.graphbuilder.perfect

import me.anno.maths.Packing.pack64
import me.anno.utils.assertions.assertEquals
import org.joml.Vector2f
import org.joml.Vector2i
import kotlin.math.sqrt

/**
 * Creates a perfectly boring rectangle grid.
 * */
open class PerfectRectangleGridBuilder : PerfectGridBuilder() {

    private val corners = arrayOf(
        Vector2i(0, 0),
        Vector2i(0, 1),
        Vector2i(1, 1),
        Vector2i(1, 0),
    )

    init {
        val corners = List(numCornersPerCell()) {
            val pos = Vector2f()
            getCornerPositionAndKey(0, 0, it, pos)
            pos
        }
        val distances = corners.map { it.lengthSquared() }
        assertEquals(distances.min(), 0.5f, 1e-6f)
        assertEquals(distances.max(), 0.5f, 1e-6f)
    }

    override fun getCellPosOrNull(dimensions: Vector2i, i: Int, j: Int, dst: Vector2f): Vector2f? {
        return dst.set(i.toFloat(), j.toFloat())
    }

    override fun getCornerPositionAndKey(i: Int, j: Int, cornerId: Int, dst: Vector2f): Long {
        val corner = corners[cornerId]
        val ik = i + corner.x
        val jk = j + corner.y
        dst.set(ik - 0.5f, jk - 0.5f)
        return pack64(ik, jk)
    }

    override fun numCornersPerCell(): Int = 4
    override fun numCellNeighbors(): Int = 4
    override fun numCornerNeighbors(): Int = 4
    override fun numEdgesPerCell(): Int = 4
}