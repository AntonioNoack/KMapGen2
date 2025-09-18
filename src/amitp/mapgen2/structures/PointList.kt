package amitp.mapgen2.structures

import me.anno.utils.types.Booleans.hasFlag
import me.anno.utils.types.Booleans.withFlag
import kotlin.math.atan2

/**
 * Base class for lists with positions, elevation, moisture (humidity), and water/ocean/coast/border flags without much memory overhead.
 * */
open class PointList(size: Int) {

    companion object {
        private const val WATER_FLAG = 1
        private const val OCEAN_FLAG = 2
        private const val COAST_FLAG = 4
        private const val BORDER_FLAG = 8
    }

    val size: Int get() = flags.size
    var indices = 0 until size
        private set

    private var floats = FloatArray(size * 4)
    private var flags = ByteArray(size)

    fun getPointX(index: Int) = floats[index * 4]
    fun getPointY(index: Int) = floats[index * 4 + 1]
    fun getElevation(index: Int) = floats[index * 4 + 2]
    fun getMoisture(index: Int) = floats[index * 4 + 3]

    fun setPoint(index: Int, x: Float, y: Float) {
        val i = index * 4
        floats[i] = x
        floats[i + 1] = y
    }

    fun setPoints(values: FloatArray) {
        for (i in indices) {
            setPoint(i, values[i * 2], values[i * 2 + 1])
        }
    }

    fun setElevation(index: Int, value: Float) {
        floats[index * 4 + 2] = value
    }

    fun setMoisture(index: Int, value: Float) {
        floats[index * 4 + 3] = value
    }

    private fun getFlags(index: Int) = flags[index].toInt()

    fun isWater(index: Int): Boolean = getFlags(index).hasFlag(WATER_FLAG)
    fun isOcean(index: Int): Boolean = getFlags(index).hasFlag(OCEAN_FLAG)
    fun isCoast(index: Int): Boolean = getFlags(index).hasFlag(COAST_FLAG)
    fun isBorder(index: Int): Boolean = getFlags(index).hasFlag(BORDER_FLAG)

    fun setWater(index: Int, value: Boolean) = setFlags(index, value, WATER_FLAG)
    fun setOcean(index: Int, value: Boolean) = setFlags(index, value, OCEAN_FLAG)
    fun setCoast(index: Int, value: Boolean) = setFlags(index, value, COAST_FLAG)
    fun setBorder(index: Int, value: Boolean) = setFlags(index, value, BORDER_FLAG)

    private fun setFlags(index: Int, value: Boolean, mask: Int) {
        flags[index] = getFlags(index).withFlag(mask, value).toByte()
    }

    fun sortByAngle(property: PackedIntLists, items: PointList) {
        for (i in indices) {
            val px = getPointX(i)
            val py = getPointY(i)
            property.sortBy(i) { j ->
                val dx = px - items.getPointX(j)
                val dy = py - items.getPointY(j)
                atan2(dy, dx)
            }
        }
    }

    fun sortByAngle(property: PackedIntLists, edges: EdgeList, corners: CornerList) {
        for (i in indices) {
            val px = getPointX(i) * 2f
            val py = getPointY(i) * 2f
            property.sortBy(i) { j ->
                val v0 = edges.getCornerA(j)
                val v1 = edges.getCornerB(j)
                if (v0 >= 0 && v1 >= 0) {
                    val dx = px - (corners.getPointX(v0) + corners.getPointX(v1))
                    val dy = py - (corners.getPointY(v0) + corners.getPointY(v1))
                    atan2(dy, dx)
                } else if (v0 >= 0f) {
                    val dx = px - corners.getPointX(v0) * 2f
                    val dy = py - corners.getPointY(v0) * 2f
                    atan2(dy, dx)
                } else if (v1 >= 0f) {
                    val dx = px - corners.getPointX(v1) * 2f
                    val dy = py - corners.getPointY(v1) * 2f
                    atan2(dy, dx)
                } else 0f
            }
        }
    }

    open fun resize(newSize: Int) {
        floats = floats.copyOf(newSize * 4)
        flags = flags.copyOf(newSize)
        indices = 0 until newSize
    }
}
