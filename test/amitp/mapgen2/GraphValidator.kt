package amitp.mapgen2

import amitp.mapgen2.structures.EdgeList
import amitp.mapgen2.structures.PackedIntLists
import me.anno.utils.assertions.assertEquals
import me.anno.utils.assertions.assertFalse
import me.anno.utils.assertions.assertTrue
import org.apache.logging.log4j.LogManager

object GraphValidator {

    private val LOGGER = LogManager.getLogger(GraphValidator::class)

    /**
     * Check that all links within a graph between cells, corners and edges make sense and are reversible.
     * */
    fun validateGraph(graph: GeneratedMap) {
        val cells = graph.cells
        val corners = graph.corners
        val edges = graph.edges
        checkListIsUnique(cells.neighbors, false)
        checkListIsUnique(cells.corners, true)
        checkListIsUnique(cells.edges, true)
        checkListIsUnique(corners.neighbors, false)
        checkListIsUnique(corners.cells, true)
        checkListIsUnique(corners.edges, true)
        checkListsAreCorresponding(cells.corners, corners.cells)
        checkCellsAreCorresponding(cells.edges, edges)
        checkCornersAreCorresponding(corners.edges, edges)
    }

    /**
     * check all entries occur exactly once, and none in itself
     * */
    fun checkListIsUnique(list: PackedIntLists, allowContainsSelf: Boolean) {
        val unique = ArrayList<Int>()
        for (i in 0 until list.size) {
            unique.clear()
            list.forEach(i) { j ->
                unique.add(j)
            }
            if (!allowContainsSelf) assertFalse(i in unique)
            assertEquals(unique.size, unique.distinct().size)
        }
    }

    /**
     * check a -> b has adequate b -> a
     * */
    fun checkListsAreCorresponding(listA: PackedIntLists, listB: PackedIntLists) {
        for (i in 0 until listA.size) {
            listA.forEach(i) { j ->
                assertTrue(listB.contains(j, i))
            }
        }
        for (j in 0 until listB.size) {
            listB.forEach(j) { i ->
                assertTrue(listA.contains(i, j))
            }
        }
    }

    /**
     * check a -> b has adequate b -> a
     * */
    fun checkCellsAreCorresponding(cellToEdges: PackedIntLists, edges: EdgeList) {
        for (cell in 0 until cellToEdges.size) {
            cellToEdges.forEach(cell) { edge ->
                val cellA = edges.getCellA(edge)
                val cellB = edges.getCellB(edge)
                assertTrue(cell == cellA || cell == cellB) {
                    "Expected x$cell in e$edge(x$cellA, x$cellB)"
                }
            }
        }
        for (edge in 0 until edges.size) {
            val cellA = edges.getCellA(edge)
            val cellB = edges.getCellB(edge)
            if (cellA >= 0) assertTrue(cellToEdges.contains(cellA, edge))
            if (cellB >= 0) assertTrue(cellToEdges.contains(cellB, edge))
        }
    }

    /**
     * check a -> b has adequate b -> a
     * */
    fun checkCornersAreCorresponding(cornerToEdges: PackedIntLists, edges: EdgeList) {
        for (corner in 0 until cornerToEdges.size) {
            cornerToEdges.forEach(corner) { edge ->
                val cornerA = edges.getCornerA(edge)
                val cornerB = edges.getCornerB(edge)
                if (!(corner == cornerA || corner == cornerB)) {
                    LOGGER.warn("Expected c$corner in e$edge(c$cornerA, c$cornerB)")
                }
            }
        }
        for (edge in 0 until edges.size) {
            val cornerA = edges.getCornerA(edge)
            val cornerB = edges.getCornerB(edge)
            if (cornerA >= 0 && !cornerToEdges.contains(cornerA, edge))
                LOGGER.warn(
                    "Expected e$edge in c$cornerA.edges ${
                        (0 until cornerToEdges.getSize(cornerA)).map { edgeIndex ->
                            "e${cornerToEdges[cornerA, edgeIndex]}"
                        }
                    }"
                )
            if (cornerB >= 0 && !cornerToEdges.contains(cornerB, edge))
                LOGGER.warn(
                    "Expected e$edge in c$cornerB.edges ${
                        (0 until cornerToEdges.getSize(cornerB)).map { edgeIndex ->
                            "e${cornerToEdges[cornerB, edgeIndex]}"
                        }
                    }"
                )
        }
    }
}