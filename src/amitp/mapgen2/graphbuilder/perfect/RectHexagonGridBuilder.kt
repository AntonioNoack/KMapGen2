package amitp.mapgen2.graphbuilder.perfect

import me.anno.maths.Packing.pack64
import me.anno.maths.chunks.hexagon.HexagonGridMaths
import me.anno.utils.assertions.assertEquals
import me.anno.utils.types.Booleans.toInt
import org.joml.Vector2f
import org.joml.Vector2i

open class RectHexagonGridBuilder : PerfectGridBuilder() {

    class Corner(val di: Int, val dj: Int, val left: Boolean)

    private val corners = arrayOf(
        Corner(0, 0, true),
        Corner(0, 0, false),
        Corner(1, 0, true),
        Corner(0, 1, false),
        Corner(0, 1, true),
        Corner(-1, 1, false)
    )

    init {
        val corners = List(numCornersPerCell()) {
            val pos = Vector2f()
            getCornerPositionAndKey(0, 0, it, pos)
            pos
        }
        val center = Vector2f()
        getCellPosOrNull(Vector2i(), 0, 0, center)
        val distances = corners.map { it.distanceSquared(center) }
        assertEquals(distances.min(), 1f, 1e-6f)
        assertEquals(distances.max(), 1f, 1e-6f)
    }

    override fun getCellPosOrNull(dimensions: Vector2i, i: Int, j: Int, dst: Vector2f): Vector2f? {
        val j = j - i.shr(1)
        return getRawPoint(i, j, dst)
    }

    private fun getRawPoint(i: Int, j: Int, dst: Vector2f): Vector2f {
        return dst.set(
            HexagonGridMaths.di.x * i + HexagonGridMaths.dj.x * j,
            HexagonGridMaths.di.y * i + HexagonGridMaths.dj.y * j
        )
    }

    override fun getCornerPositionAndKey(i: Int, j: Int, cornerId: Int, dst: Vector2f): Long {

        val corner = corners[cornerId]
        val ik = i + corner.di
        val jk = j - i.shr(1) + corner.dj
        val cornerIndex = if (corner.left) 4 else 5

        getRawPoint(ik, jk, dst) // center

        val deltaPos = HexagonGridMaths.corners[cornerIndex]
        dst.add(deltaPos.x.toFloat(), deltaPos.y.toFloat())

        return pack64(ik, jk * 2 + corner.left.toInt())
    }

    override fun numCornersPerCell(): Int = 6
    override fun numCellNeighbors(): Int = 6
    override fun numCornerNeighbors(): Int = 3
    override fun numEdgesPerCell(): Int = 6
}