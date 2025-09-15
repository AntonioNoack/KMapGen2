package amitp.mapgen2.geometry

class EdgeList(val size: Int) {

    private val corners = IntArray(size * 2)
    private val rivers = BooleanArray(size)

    fun setV0(index: Int, value: Corner?) {
        corners[index.shl(1)] = value?.index ?: -1
    }

    fun setV1(index: Int, value: Corner?) {
        corners[index.shl(1) + 1] = value?.index ?: -1
    }

    fun hasRivers(index: Int): Boolean = rivers[index]
    fun addRiver(index: Int) {
        rivers[index] = true
    }

    fun getV0(index: Int): Int = corners[index.shl(1)]
    fun getV1(index: Int): Int = corners[index.shl(1) + 1]
}