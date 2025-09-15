package amitp.mapgen2.geometry

import amitp.mapgen2.utils.PackedIntLists

class CornerList(size: Int) : PointList(size) {

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
