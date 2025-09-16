package amitp.mapgen2.structures

import org.joml.Vector2f

/**
 * Temporary storage of regions (cell indices) and vertices (positions) for Voronoi cell algorithm
 * */
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
     * circumcell of triangle on one side OR
     * circumcell of triangle on other side
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
