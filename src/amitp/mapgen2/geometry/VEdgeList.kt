package amitp.mapgen2.geometry

import org.joml.Vector2f

class VEdgeList(val size: Int) {

    // size indices
    private val regions = IntArray(size * 2)
    private val vertices = FloatArray(size * 4)

    fun getRegionL(index: Int): Int = regions[index shl 1]
    fun getRegionR(index: Int): Int = regions[(index shl 1) + 1]

    fun setRegionL(index: Int, value: Int) {
        regions[index shl 1] = value
    }

    fun setRegionR(index: Int, value: Int) {
        regions[(index shl 1) + 1] = value
    }

    /**
     * circumcenter of triangle on one side OR
     * circumcenter of triangle on other side
     * */
    fun getVertex(index: Int, comp: Int): Float = vertices[(index shl 2) + comp]

    fun setVertices(index: Int, valueA: Vector2f, valueB: Vector2f) {
        val k = index shl 2
        vertices[k] = valueA.x
        vertices[k + 1] = valueA.y
        vertices[k + 2] = valueB.x
        vertices[k + 3] = valueB.y
    }
}
