package amitp.mapgen2.geometry

import amitp.mapgen2.utils.PackedIntLists
import me.anno.utils.assertions.assertEquals

/**
 * todo when we replaced the corner list, we got massively different results
 *  -> replace it step by step, for that introduce conversion functions (should be easy)
 * */
class CornerList(size: Int) : PointList(size) {

    companion object {
        @Deprecated("Remove these")
        fun List<Corner>.toCornerList(dst: CornerList) {
            assertEquals(size, dst.size)

            dst.touches.clear()
            dst.protrudes.clear()
            dst.adjacent.clear()

            for (i in 0 until size) {
                val src = this[i]
                dst.setPoint(i, src.point.x, src.point.y)
                dst.setElevation(i, src.elevation)
                dst.setMoisture(i, src.moisture)
                dst.setRiver(i, src.river)
                dst.setWatershed(i, src.watershed?.index ?: -1)
                dst.setWatershedSize(i, src.watershedSize)
                dst.setDownslope(i, src.downslope?.index ?: -1)

                dst.setWater(i, src.water)
                dst.setOcean(i, src.ocean)
                dst.setCoast(i, src.coast)
                dst.setBorder(i, src.border)

                src.protrudes.map { dst.protrudes.add(i, it) }
                src.touches.map { dst.touches.add(i, it) }
                src.adjacent.map { dst.adjacent.add(i, it.index) }
            }
        }

        @Deprecated("Remove these")
        fun CornerList.toCorners(dst: List<Corner>) {
            assertEquals(size, dst.size)
            for (i in 0 until size) {
                val dstI = dst[i]
                dstI.point.x = getPointX(i)
                dstI.point.y = getPointY(i)
                dstI.elevation = getElevation(i)
                dstI.moisture = getMoisture(i)
                dstI.river = getRiver(i)
                dstI.watershed = dst.getOrNull(getWatershed(i))
                dstI.watershedSize = getWatershedSize(i)
                dstI.downslope = dst.getOrNull(getDownslope(i))

                dstI.water = isWater(i)
                dstI.ocean = isOcean(i)
                dstI.coast = isCoast(i)
                dstI.border = isBorder(i)

                dstI.adjacent.clear()
                dstI.protrudes.clear()
                dstI.touches.clear()

                adjacent.forEach(i) { dstI.adjacent.add(dst[it]) }
                protrudes.forEach(i) { dstI.protrudes.add(it) }
                touches.forEach(i) { dstI.touches.add(it) }
            }
        }
    }

    private val ints = IntArray(size * 4)

    fun getRiver(index: Int) = ints[index * 4]
    fun setRiver(index: Int, value: Int) {
        ints[index * 4] = value
    }

    fun getDownslope(index: Int) = ints[index * 4 + 1]
    fun setDownslope(index: Int, value: Int) {
        ints[index * 4 + 1] = value
    }

    fun getWatershed(index: Int) = ints[index * 4 + 2]
    fun setWatershed(index: Int, value: Int) {
        ints[index * 4 + 2] = value
    }

    fun getWatershedSize(index: Int) = ints[index * 4 + 3]
    fun setWatershedSize(index: Int, value: Int) {
        ints[index * 4 + 3] = value
    }

    fun addRiver(index: Int) {
        setRiver(index, getRiver(index) + 1)
    }

    /**
     * centers
     * */
    val touches = PackedIntLists(size, 4)

    /**
     * corners
     * */
    val adjacent = PackedIntLists(size, 4)

    /**
     * edges
     * */
    val protrudes = PackedIntLists(size, 4)
}
