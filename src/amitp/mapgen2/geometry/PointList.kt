package amitp.mapgen2.geometry

import amitp.mapgen2.geometry.CenterList.Companion.BORDER_FLAG
import amitp.mapgen2.geometry.CenterList.Companion.COAST_FLAG
import amitp.mapgen2.geometry.CenterList.Companion.OCEAN_FLAG
import amitp.mapgen2.geometry.CenterList.Companion.WATER_FLAG
import me.anno.utils.types.Booleans.hasFlag
import me.anno.utils.types.Booleans.withFlag

open class PointList(val size: Int) {

    private val floats = FloatArray(size * 4)
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
        for (i in 0 until size) {
            setPoint(i, values[i * 2], values[i * 2 + 1])
        }
    }

    fun setElevation(index: Int, value: Float) {
        floats[index * 4 + 2] = value
    }

    fun setMoisture(index: Int, value: Float) {
        floats[index * 4 + 3] = value
    }

    private val flags = ByteArray(size)
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