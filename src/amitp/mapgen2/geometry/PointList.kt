package amitp.mapgen2.geometry

import me.anno.utils.types.Booleans.hasFlag
import me.anno.utils.types.Booleans.withFlag

open class PointList(val size: Int) {

    companion object {
        private const val WATER_FLAG = 1
        private const val OCEAN_FLAG = 2
        private const val COAST_FLAG = 4
        private const val BORDER_FLAG = 8
    }

    private val floats = FloatArray(size * 4)
    private val flags = ByteArray(size)

    fun getPointX(index: Int) = floats[index * 4]
    fun getPointY(index: Int) = floats[index * 4 + 1]
    fun getElevation(index: Int) = floats[index * 4 + 2]
    fun getMoisture(index: Int) = floats[index * 4 + 3]

    fun setPoint(index: Int, x: Float, y: Float) {
        val i = index * 4
        floats[i] = x
        floats[i + 1] = y
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
}
