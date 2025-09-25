package amitp.mapgen2.graphbuilder

import amitp.mapgen2.GeneratedMap
import amitp.mapgen2.graphbuilder.voronoi.GridVoronoi
import amitp.mapgen2.graphbuilder.voronoi.Voronoi
import amitp.mapgen2.pointselector.PointSelector
import amitp.mapgen2.structures.CellList
import amitp.mapgen2.structures.CornerList
import amitp.mapgen2.structures.EdgeList
import me.anno.maths.Maths.clamp
import me.anno.maths.Packing.pack64
import me.anno.maths.Packing.unpackHighFrom64
import me.anno.maths.Packing.unpackLowFrom64
import me.anno.utils.Clock
import me.anno.utils.algorithms.ForLoop.forLoopSafely
import org.apache.logging.log4j.LogManager
import org.joml.Vector2f
import speiger.primitivecollections.LongToIntHashMap

class VoronoiGraphBuilder(
    val pointSelector: PointSelector,
    val useExperimentalVoronoi: Boolean = false
) : GraphBuilder {

    companion object {
        private val LOGGER = LogManager.getLogger(VoronoiGraphBuilder::class)

        fun buildGraph(points: FloatArray, voronoi: Voronoi, size: Vector2f): GeneratedMap {

            // Create Cell objects for each point
            val cells = CellList(points.size shr 1)
            for (i in cells.indices) {
                cells.setPoint(i, points[i * 2], points[i * 2 + 1])
            }

            // Helper: create or reuse Corner object
            val cornerMap = LongToIntHashMap(-1)

            fun ensureCorner(px: Float, py: Float) {
                if (px.isNaN() || py.isNaN()) return
                val key = pack64(px.toRawBits(), py.toRawBits())
                cornerMap.getOrPut(key) { cornerMap.size }
            }

            fun getCorner(px: Float, py: Float): Int {
                val key = pack64(px.toRawBits(), py.toRawBits())
                return cornerMap[key]
            }

            // Build edges
            val srcEdges = voronoi.edges
            val edges = EdgeList(srcEdges.size)

            for (edgeIndex in 0 until srcEdges.size) {
                ensureCorner(srcEdges.getPosition(edgeIndex, 0), srcEdges.getPosition(edgeIndex, 1))
                ensureCorner(srcEdges.getPosition(edgeIndex, 2), srcEdges.getPosition(edgeIndex, 3))
            }

            val corners = CornerList(cornerMap.size)
            cornerMap.forEach { pos, i ->
                val px = Float.fromBits(unpackHighFrom64(pos))
                val py = Float.fromBits(unpackLowFrom64(pos))
                corners.setPoint(i, px, py)
                corners.setBorder(i, px <= 0f || px >= size.x || py <= 0f || py >= size.y)
            }

            for (edge in 0 until srcEdges.size) {
                val cellA = srcEdges.getCellA(edge)
                val cellB = srcEdges.getCellB(edge)

                val cornerA = getCorner(srcEdges.getPosition(edge, 0), srcEdges.getPosition(edge, 1))
                val cornerB = getCorner(srcEdges.getPosition(edge, 2), srcEdges.getPosition(edge, 3))

                // println("e$edge: x$cellA,x$cellB, c$cornerA,c$cornerB")

                edges.setCellA(edge, cellA)
                edges.setCellB(edge, cellB)
                edges.setCornerA(edge, cornerA)
                edges.setCornerB(edge, cornerB)

                // Cells point to edges
                if (cellA >= 0) cells.edges.add(cellA, edge)
                if (cellB >= 0) cells.edges.add(cellB, edge)

                // Corners point to edges
                if (cornerA >= 0) corners.edges.add(cornerA, edge)
                if (cornerB >= 0) corners.edges.add(cornerB, edge)

                // Cells point to cells
                if (cellA >= 0 && cellB >= 0) {
                    cells.neighbors.addUnique(cellA, cellB)
                    cells.neighbors.addUnique(cellB, cellA)
                }

                // Corners point to corners
                if (cornerA >= 0 && cornerB >= 0) {
                    corners.neighbors.addUnique(cornerA, cornerB)
                    corners.neighbors.addUnique(cornerB, cornerA)
                }

                // Cells point to corners
                if (cellA >= 0) {
                    if (cornerA >= 0) cells.corners.addUnique(cellA, cornerA)
                    if (cornerB >= 0) cells.corners.addUnique(cellA, cornerB)
                }
                if (cellB >= 0) {
                    if (cornerA >= 0) cells.corners.addUnique(cellB, cornerA)
                    if (cornerB >= 0) cells.corners.addUnique(cellB, cornerB)
                }

                // Corners point to cells
                if (cornerA >= 0) {
                    corners.cells.addUnique(cornerA, cellA)
                    corners.cells.addUnique(cornerA, cellB)
                }
                if (cornerB >= 0) {
                    corners.cells.addUnique(cornerB, cellA)
                    corners.cells.addUnique(cornerB, cellB)
                }
            }

            return GeneratedMap(cells, corners, edges, size)
        }

    }

    override fun buildGraph(size: Vector2f, numCells: Int, seed: Long): GeneratedMap {
        val clock = Clock(LOGGER)
        val points = pointSelector.select(size, numCells, seed)
        clock.stop("Generate Points")

        val map = if (useExperimentalVoronoi) {
            GridVoronoi(points, size).map
        } else {
            val voronoi = Voronoi(points, size)
            buildGraph(points, voronoi, size)
        }
        clock.stop("Create Graph")
        printStats(map)

        improveCorners(map.cells, map.corners, size)
        clock.stop("Improve Corners")
        return map
    }

    private fun printStats(map: GeneratedMap) {
        val numCells = map.cells.size
        val numCorners = map.corners.size
        val cells = map.cells
        val corners = map.corners
        val edges = map.edges

        LOGGER.info("Stats: [$numCells cells + $numCorners corners + ${edges.size} edges]")
        LOGGER.info("Cells.neighbors: ${(0 until numCells).sumOf { cells.neighbors.getSize(it) }}")
        LOGGER.info("Cells.corners: ${(0 until numCells).sumOf { cells.corners.getSize(it) }}")
        LOGGER.info("Cells.edges: ${(0 until numCells).sumOf { cells.edges.getSize(it) }}")

        LOGGER.info("Corners.cells: ${(0 until numCorners).sumOf { corners.cells.getSize(it) }}")
        LOGGER.info("Corners.adjacent: ${(0 until numCorners).sumOf { corners.neighbors.getSize(it) }}")
        LOGGER.info("Corners.edges: ${(0 until numCorners).sumOf { corners.edges.getSize(it) }}")
    }

    /**
     * Although Lloyd relaxation improves the uniformity of polygon
     * sizes, it doesn't help with the edge lengths. Short edges can
     * be bad for some games, and lead to weird artifacts on
     * rivers. We can easily lengthen short edges by moving the
     * corners, but **we lose the Voronoi property**.  The corners are
     * moved to the average of the polygon cells around them. Short
     * edges become longer. Long edges tend to become shorter. The
     * polygons tend to be more uniform after this step.
     * */
    private fun improveCorners(cells: CellList, corners: CornerList, size: Vector2f) {
        val newCorners = FloatArray(corners.size shl 1)
        for (q in corners.indices) {
            if (corners.isBorder(q)) {
                newCorners[q * 2] = corners.getPointX(q)
                newCorners[q * 2 + 1] = corners.getPointY(q)
            } else {
                var sumX = 0f
                var sumY = 0f
                val numTouches = corners.cells.forEach(q) { r ->
                    sumX += cells.getPointX(r)
                    sumY += cells.getPointY(r)
                }
                newCorners[q * 2] = sumX / numTouches
                newCorners[q * 2 + 1] = sumY / numTouches
            }
        }
        forLoopSafely(newCorners.size, 2) { i ->
            newCorners[i] = clamp(newCorners[i], 0f, size.x)
            newCorners[i + 1] = clamp(newCorners[i + 1], 0f, size.y)
        }
        corners.setPoints(newCorners)
    }
}