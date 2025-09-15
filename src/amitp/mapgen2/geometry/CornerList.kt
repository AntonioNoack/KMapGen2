package amitp.mapgen2.geometry

import amitp.mapgen2.utils.PackedIntLists

class CornerList(size: Int) : PointList(size) {

    private val ints = IntArray(size * 4)
    fun getNumRivers(index: Int) = ints[index * 4]
    fun addRiver(index: Int) {
        ints[index * 4]++
    }

    /**
     * corner
     * */
    fun getDownslope(index: Int): Int {
        return ints[index * 4 + 1]
    }

    fun setDownslope(index: Int, value: Int) {
        ints[index * 4 + 1] = value
    }

    /**
     * corner
     * */
    fun getWatershed(index: Int) = ints[index * 4 + 2]
    fun setWatershed(index: Int, value: Int) {
        ints[index * 4 + 2] = value
    }

    fun getWatershedSize(index: Int): Int = ints[index * 4 + 3]
    fun setWatershedSize(index: Int, value: Int) {
        ints[index * 4 + 3] = value
    }

    init {
        for (i in 0 until size) {
            setDownslope(i, -1)
            setWatershed(i, -1)
        }
    }

    /**
     * centers; pretty much all entries will have 3 vertices, so use that +1 for the end indices
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
