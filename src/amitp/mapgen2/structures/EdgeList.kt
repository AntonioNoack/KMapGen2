package amitp.mapgen2.structures

import me.anno.utils.types.Booleans.hasFlag

class EdgeList(val size: Int) {

    companion object {
        private const val FLAG_RIVER = 1
        private const val FLAG_ROAD = 2
        private const val FLAG_LAVA = 4
    }

    private val ints = IntArray(size * 4)
    private val flags = ByteArray(size)

    fun setCornerA(index: Int, value: Int) {
        ints[index.shl(2)] = value
    }

    fun setCornerB(index: Int, value: Int) {
        ints[index.shl(2) + 1] = value
    }

    fun setCellA(index: Int, value: Int) {
        ints[index.shl(2) + 2] = value
    }

    fun setCellB(index: Int, value: Int) {
        ints[index.shl(2) + 3] = value
    }

    fun hasRiver(index: Int): Boolean = flags[index].toInt().hasFlag(FLAG_RIVER)
    fun setRiver(index: Int) {
        flags[index] = (flags[index].toInt() or FLAG_RIVER).toByte()
    }

    fun hasRoad(index: Int): Boolean = flags[index].toInt().hasFlag(FLAG_ROAD)
    fun setRoad(index: Int) {
        flags[index] = (flags[index].toInt() or FLAG_ROAD).toByte()
    }

    fun hasLava(index: Int): Boolean = flags[index].toInt().hasFlag(FLAG_LAVA)
    fun setLava(index: Int) {
        flags[index] = (flags[index].toInt() or FLAG_LAVA).toByte()
    }

    /**
     * Get the corner index A, may be -1; called v0 in the original
     * */
    fun getCornerA(index: Int): Int = ints[index.shl(2)]

    /**
     * Get the corner index B, may be -1; called v1 in the original
     * */
    fun getCornerB(index: Int): Int = ints[index.shl(2) + 1]

    /**
     * Get the cell index A; called d0 original
     * */
    fun getCellA(index: Int): Int = ints[index.shl(2) + 2]

    /**
     * Get the cell index B; called d1 in the original
     * */
    fun getCellB(index: Int): Int = ints[index.shl(2) + 3]
}