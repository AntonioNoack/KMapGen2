package amitp.mapgen2.structures

/**
 * Storage for the corners
 * */
class CornerList(size: Int) : PointList(size) {

    private val ints = IntArray(size * 4)

    fun getRiverFlowStrength(index: Int) = ints[index * 4]
    fun incRiverFlowStrength(index: Int) {
        ints[index * 4]++
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

    /**
     * cells for each corner, 3 on average
     * */
    val cells = PackedIntLists(size, 4)

    /**
     * neighbor corners for each corner, 3 on average
     * */
    val neighbors = PackedIntLists(size, 4)

    /**
     * edges for each corner, 3 on average
     * */
    val edges = PackedIntLists(size, 4)
}
