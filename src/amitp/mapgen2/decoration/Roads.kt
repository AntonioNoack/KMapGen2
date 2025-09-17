package amitp.mapgen2.decoration

import amitp.mapgen2.GeneratedMap
import amitp.mapgen2.MapGen2.flattenLines
import amitp.mapgen2.structures.CellList
import amitp.mapgen2.structures.EdgeList

object Roads {

    /**
     * last entry should be unreachable (aka > 1)
     * */
    val defaultElevationLevels = floatArrayOf(0f, 0.05f, 0.37f, 0.64f, 1000f)

    /**
     * We want to mark different elevation zones so that we can draw
     * island-circling roads that divide the areas.
     * */
    fun generateRoads(map: GeneratedMap, elevationLevels: FloatArray = defaultElevationLevels) {
        val cellContour = findContourLevels(map, elevationLevels)
        placeRoadsOnContourLevels(map, cellContour)
        flattenLines(map, true) { edge -> map.edges.isRoad(edge) }
    }

    fun findContourLevels(map: GeneratedMap, elevationLevels: FloatArray): IntArray {
        val cells = map.cells

        // Oceans and coastal polygons are the lowest contour zone
        // (1) Anything connected to contour level K, if it's below elevation threshold K, or if it's water, gets contour level K.
        // (2) Anything not assigned a contour level, and connected to contour level K, gets contour level K+1.
        val queue = ArrayDeque<Int>()
        val cellContour = IntArray(cells.size) // cell index -> int contour level

        for (p in 0 until cells.size) {
            if (cells.isCoast(p) || cells.isOcean(p)) {
                cellContour[p] = 1
                queue.add(p)
            }
        }

        while (queue.isNotEmpty()) {
            val p = queue.removeFirst()
            cells.neighbors.forEach(p) { r ->
                var newLevel = cellContour[p]
                while (cells.getElevation(r) > elevationLevels[newLevel] && !cells.isWater(r)) {
                    // NOTE: extend the contour line past bodies of
                    // water so that roads don't terminate inside lakes.
                    newLevel++
                }
                if (newLevel < cellContour[r].ifZero(Int.MAX_VALUE)) {
                    cellContour[r] = newLevel
                    queue.add(r)
                }
            }
        }

        return cellContour
    }

    /**
     * Roads go between polygons that have different contour levels
     * */
    fun placeRoadsOnContourLevels(map: GeneratedMap, cellContour: IntArray) {
        val cells = map.cells
        val edges = map.edges
        for (p in 0 until cells.size) {
            cells.neighbors.forEach(p) { q ->
                if (cellContour[p] != cellContour[q]) {
                    val edge = findEdge(cells, edges, p, q)
                    if (edge >= 0) edges.setRoad(edge)
                }
            }
        }
    }

    fun findEdge(cells: CellList, edges: EdgeList, p: Int, q: Int): Int {
        cells.edges.forEach(p) { edgeIndex ->
            val v0 = edges.getCellA(edgeIndex)
            val v1 = edges.getCellB(edgeIndex)
            if ((v0 == p && v1 == q) || (v0 == q && v1 == p)) {
                return edgeIndex
            }
        }
        return -1
    }

    private fun Int.ifZero(x: Int): Int {
        return if (this == 0) x else this
    }
}