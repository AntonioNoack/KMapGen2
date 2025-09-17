package amitp.mapgen2.decoration

import amitp.mapgen2.GeneratedMap
import amitp.mapgen2.structures.CellList
import amitp.mapgen2.structures.EdgeList

object Roads {

    /**
     * We want to mark different elevation zones so that we can draw
     * island-circling roads that divide the areas.
     * */
    fun generateRoads(map: GeneratedMap) {

        val cells = map.cells
        val corners = map.corners
        val edges = map.edges

        // Oceans and coastal polygons are the lowest contour zone
        // (1) Anything connected to contour level K, if it's below elevation threshold K, or if it's water, gets contour level K.
        // (2) Anything not assigned a contour level, and connected to contour level K, gets contour level K+1.
        val queue = ArrayDeque<Int>()
        val elevationThresholds = floatArrayOf(0f, 0.05f, 0.37f, 0.64f, 1000f)
        val cornerContour = IntArray(corners.size) // corner index -> int contour level
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
                while (cells.getElevation(r) > elevationThresholds[newLevel] && !cells.isWater(r)) {
                    // NOTE: extend the contour line past bodies of
                    // water so that roads don't terminate inside lakes.
                    newLevel++
                }
                if (newLevel < cellContour[r].ifZero(999)) {
                    cellContour[r] = newLevel
                    queue.add(r)
                }
            }
        }


        // Roads go between polygons that have different contour levels
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