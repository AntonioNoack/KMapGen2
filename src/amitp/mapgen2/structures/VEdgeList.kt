package amitp.mapgen2.structures

import org.joml.Vector2f

/**
 * Temporary storage of regions (cell indices) and vertices (positions) for Voronoi cell algorithm
 * */
class VEdgeList(val size: Int) {

    // size indices
    private val cells = IntArray(size * 2)
    private val cornerPositions = FloatArray(size * 4)

    fun getCellA(index: Int): Int = cells[index shl 1]
    fun getCellB(index: Int): Int = cells[(index shl 1) + 1]

    fun set(index: Int, cellA: Int, cellB: Int, valueA: Vector2f, valueB: Vector2f) {
        cells[index shl 1] = cellA
        cells[(index shl 1) + 1] = cellB
        val k = index shl 2
        cornerPositions[k] = valueA.x
        cornerPositions[k + 1] = valueA.y
        cornerPositions[k + 2] = valueB.x
        cornerPositions[k + 3] = valueB.y
    }

    /**
     * circumcenter of triangle on one side OR
     * circumcenter of triangle on other side
     * */
    fun getPosition(index: Int, comp: Int): Float = cornerPositions[(index shl 2) + comp]
}
