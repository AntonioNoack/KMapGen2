package amitp.mapgen2.graph

import amitp.mapgen2.geometry.CenterList
import amitp.mapgen2.geometry.CornerList
import amitp.mapgen2.geometry.EdgeList
import amitp.mapgen2.pointselector.PointSelector
import amitp.mapgen2.utils.PackedIntLists
import me.anno.maths.Packing.pack64
import speiger.primitivecollections.LongToIntHashMap

class VoronoiGraphBuilder(
    val pointSelector: PointSelector
) : GraphBuilder {

    override fun buildGraph(size: Float, numCells: Int, seed: Long): Graph {
        val t0 = System.nanoTime()
        val points = pointSelector.select(size, numCells, seed)
        val t1 = System.nanoTime()
        val voronoi = Voronoi(points, size)
        val t2 = System.nanoTime()
        val graph = buildGraph(points, voronoi, size)
        val t3 = System.nanoTime()
        improveCorners(graph)
        val t4 = System.nanoTime()
        println("[GraphBuilder] ${numCells}x ${(t1 - t0) / 1e6f} ms + ${(t2 - t1) / 1e6f} ms + ${(t3 - t2) / 1e6f} ms + ${(t4 - t3) / 1e6f} ms")
        return graph
    }

    /**
     * Although Lloyd relaxation improves the uniformity of polygon
     * sizes, it doesn't help with the edge lengths. Short edges can
     * be bad for some games, and lead to weird artifacts on
     * rivers. We can easily lengthen short edges by moving the
     * corners, but **we lose the Voronoi property**.  The corners are
     * moved to the average of the polygon centers around them. Short
     * edges become longer. Long edges tend to become shorter. The
     * polygons tend to be more uniform after this step.
     * */
    private fun improveCorners(graph: Graph) {
        val corners = graph.corners
        val centers = graph.centers
        val newCorners = FloatArray(corners.size * 2)
        for (q in 0 until corners.size) {
            if (corners.isBorder(q)) {
                newCorners[q * 2] = corners.getPointX(q)
                newCorners[q * 2 + 1] = corners.getPointY(q)
            } else {
                var avgX = 0f
                var avgY = 0f
                val numTouches = corners.touches.forEach(q) { r ->
                    avgX += centers.getPointX(r)
                    avgY += centers.getPointY(r)
                }
                newCorners[q * 2] = avgX / numTouches
                newCorners[q * 2 + 1] = avgY / numTouches
            }
        }
        corners.setPoints(newCorners)
    }

    private fun buildGraph(points: FloatArray, voronoi: Voronoi, size: Float): Graph {

        // Create Center objects for each point
        val centers = CenterList(points.size shr 1)
        centers.setPoints(points)

        // Helper: create or reuse Corner object
        val cornerMap = LongToIntHashMap(-1)

        fun ensureCorner(px: Float, py: Float): Int {
            if (px.isNaN() || py.isNaN()) return -1
            val key = pack64(px.toRawBits(), py.toRawBits())
            return cornerMap.getOrPut(key) { cornerMap.size }
        }

        val edges0 = voronoi.edges
        for (edgeIndex in 0 until edges0.size) {
            ensureCorner(edges0.getVertex(edgeIndex, 0), edges0.getVertex(edgeIndex, 1))
            ensureCorner(edges0.getVertex(edgeIndex, 2), edges0.getVertex(edgeIndex, 3))
        }

        fun getCorner(px: Float, py: Float): Int {
            if (px.isNaN() || py.isNaN()) return -1
            val key = pack64(px.toRawBits(), py.toRawBits())
            return cornerMap[key]
        }

        fun addIfNotPresent(list: PackedIntLists, index: Int, c: Int) {
            if (c >= 0 && !list.contains(index, c)) {
                list.add(index, c)
            }
        }

        // Build edges
        val edges = EdgeList(edges0.size)
        val corners = CornerList(cornerMap.size)

        for (edgeIndex in 0 until edges0.size) {
            val d0 = edges0.getRegionL(edgeIndex)
            val d1 = edges0.getRegionR(edgeIndex)

            val v0 = getCorner(edges0.getVertex(edgeIndex, 0), edges0.getVertex(edgeIndex, 1))
            val v1 = getCorner(edges0.getVertex(edgeIndex, 2), edges0.getVertex(edgeIndex, 3))

            edges.setV0(edgeIndex, v0)
            edges.setV1(edgeIndex, v1)

            // Centers point to edges
            if (d0 >= 0) centers.borders.add(d0, edgeIndex)
            if (d1 >= 0) centers.borders.add(d1, edgeIndex)

            // Corners point to edges
            if (v0 >= 0) corners.protrudes.add(v0, edgeIndex)
            if (v1 >= 0) corners.protrudes.add(v1, edgeIndex)

            // Centers point to centers
            if (d0 >= 0 && d1 >= 0) {
                addIfNotPresent(centers.neighbors, d0, d1)
                addIfNotPresent(centers.neighbors, d1, d0)
            }

            // Corners point to corners
            if (v0 >= 0 && v1 >= 0) {
                addIfNotPresent(corners.adjacent, v0, v1)
                addIfNotPresent(corners.adjacent, v1, v0)
            }

            // Centers point to corners
            if (d0 >= 0) {
                if (v0 >= 0) addIfNotPresent(centers.corners, d0, v0)
                if (v1 >= 0) addIfNotPresent(centers.corners, d0, v1)
            }
            if (d1 >= 0) {
                if (v0 >= 0) addIfNotPresent(centers.corners, d1, v0)
                if (v1 >= 0) addIfNotPresent(centers.corners, d1, v1)
            }

            // Corners point to centers
            if (v0 >= 0) {
                addIfNotPresent(corners.touches, v0, d0)
                addIfNotPresent(corners.touches, v0, d1)
            }
            if (v1 >= 0) {
                addIfNotPresent(corners.touches, v1, d0)
                addIfNotPresent(corners.touches, v1, d1)
            }
        }

        return Graph(centers, corners, edges)
    }

}