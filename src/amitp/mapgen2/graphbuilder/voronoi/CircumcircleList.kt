package amitp.mapgen2.graphbuilder.voronoi

import amitp.mapgen2.structures.PackedIntLists

/**
 * Linear list of circumcircles.
 * Each circumcircle has an optional link to another circle.
 * */
class CircumcircleList {

    var size = 0
    val capacity get() = links.size

    private var floats = FloatArray(64 * 3)
    private var links = IntArray(64)

    val cells = PackedIntLists(64, 4)

    fun add(x: Float, y: Float, radiusSq: Float, next: Int): Int {
        if (size == capacity) doubleSize()
        val index = size++
        links[index] = next
        val i3 = index * 3
        floats[i3] = x
        floats[i3 + 1] = y
        floats[i3 + 2] = radiusSq
        return index
    }

    fun getX(i: Int) = floats[i * 3]
    fun getY(i: Int) = floats[i * 3 + 1]
    fun getRadiusSq(i: Int) = floats[i * 3 + 2]
    fun getNext(i: Int): Int = links[i]

    private fun doubleSize() {
        val newSize = links.size * 2
        links = links.copyOf(newSize)
        floats = floats.copyOf(newSize * 3)
        cells.resizeTo(newSize)
    }
}