package amitp.mapgen2.graphbuilder

import amitp.mapgen2.GeneratedMap
import amitp.mapgen2.pointselector.PointSelector
import amitp.mapgen2.structures.CellList
import amitp.mapgen2.structures.CornerList
import amitp.mapgen2.structures.EdgeList
import me.anno.maths.Maths.clamp
import me.anno.maths.Packing.pack64
import me.anno.maths.Packing.unpackHighFrom64
import me.anno.maths.Packing.unpackLowFrom64
import me.anno.utils.Clock
import org.apache.logging.log4j.LogManager
import speiger.primitivecollections.LongToIntHashMap

class VoronoiGraphBuilder(
    val pointSelector: PointSelector
) : GraphBuilder {

    companion object {
        private val LOGGER = LogManager.getLogger(VoronoiGraphBuilder::class)
    }

    override fun buildGraph(size: Float, numCells: Int, seed: Long): GeneratedMap {
        val clock = Clock(LOGGER)
        val points = pointSelector.select(size, numCells, seed)
        clock.stop("Generate Points")

        val map = if (false) {
            // not yet working correctly,
            // is promising for performance though
            GridVoronoi(points).map
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
        val numPoints = map.cells.size
        val numCorners = map.corners.size
        val cells = map.cells
        val corners = map.corners
        val edges = map.edges

        LOGGER.info("Stats: [$numPoints cells + $numCorners corners + ${edges.size} edges]")
        LOGGER.info("Cells.neighbors: ${(0 until numPoints).sumOf { cells.neighbors.getSize(it) }}")
        LOGGER.info("Cells.corners: ${(0 until numPoints).sumOf { cells.corners.getSize(it) }}")
        LOGGER.info("Cells.edges: ${(0 until numPoints).sumOf { cells.edges.getSize(it) }}")

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
    private fun improveCorners(cells: CellList, corners: CornerList, size: Float) {
        val newCorners = FloatArray(corners.size shl 1)
        for (q in 0 until corners.size) {
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
        for (i in newCorners.indices) {
            newCorners[i] = clamp(newCorners[i], 0f, size)
        }
        corners.setPoints(newCorners)
    }

    private fun buildGraph(points: FloatArray, voronoi: Voronoi, size: Float): GeneratedMap {

        // Create Cell objects for each point
        val cells = CellList(points.size shr 1)
        for (i in 0 until cells.size) {
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
        val edges0 = voronoi.edges
        val edges = EdgeList(edges0.size)

        for (edgeIndex in 0 until edges0.size) {
            ensureCorner(edges0.getVertex(edgeIndex, 0), edges0.getVertex(edgeIndex, 1))
            ensureCorner(edges0.getVertex(edgeIndex, 2), edges0.getVertex(edgeIndex, 3))
        }

        val cornerList = CornerList(cornerMap.size)
        cornerMap.forEach { pos, i ->
            val px = Float.fromBits(unpackHighFrom64(pos))
            val py = Float.fromBits(unpackLowFrom64(pos))
            cornerList.setPoint(i, px, py)
            cornerList.setBorder(i, px <= 0f || px >= size || py <= 0f || py >= size)
        }

        for (edgeIndex in 0 until edges0.size) {
            val d0 = edges0.getRegionL(edgeIndex)
            val d1 = edges0.getRegionR(edgeIndex)

            val v0 = getCorner(edges0.getVertex(edgeIndex, 0), edges0.getVertex(edgeIndex, 1))
            val v1 = getCorner(edges0.getVertex(edgeIndex, 2), edges0.getVertex(edgeIndex, 3))

            edges.setCellA(edgeIndex, d0)
            edges.setCellB(edgeIndex, d1)
            edges.setCornerA(edgeIndex, v0)
            edges.setCornerB(edgeIndex, v1)

            // Cells point to edges
            if (d0 >= 0) cells.edges.add(d0, edgeIndex)
            if (d1 >= 0) cells.edges.add(d1, edgeIndex)

            // Corners point to edges
            if (v0 >= 0) cornerList.edges.add(v0, edgeIndex)
            if (v1 >= 0) cornerList.edges.add(v1, edgeIndex)

            // Cells point to cells
            if (d0 >= 0 && d1 >= 0) {
                cells.neighbors.addUnique(d0, d1)
                cells.neighbors.addUnique(d1, d0)
            }

            // Corners point to corners
            if (v0 >= 0 && v1 >= 0) {
                cornerList.neighbors.addUnique(v0, v1)
                cornerList.neighbors.addUnique(v1, v0)
            }

            // Cells point to corners
            if (d0 >= 0) {
                if (v0 >= 0) cells.corners.addUnique(d0, v0)
                if (v1 >= 0) cells.corners.addUnique(d0, v1)
            }
            if (d1 >= 0) {
                if (v0 >= 0) cells.corners.addUnique(d1, v0)
                if (v1 >= 0) cells.corners.addUnique(d1, v1)
            }

            // Corners point to cells
            if (v0 >= 0) {
                cornerList.cells.addUnique(v0, d0)
                cornerList.cells.addUnique(v0, d1)
            }
            if (v1 >= 0) {
                cornerList.cells.addUnique(v1, d0)
                cornerList.cells.addUnique(v1, d1)
            }
        }

        return GeneratedMap(cells, cornerList, edges)
    }

}