package amitp.mapgen2.graph

import amitp.mapgen2.geometry.Center
import amitp.mapgen2.geometry.Corner
import amitp.mapgen2.geometry.EdgeList
import amitp.mapgen2.pointselector.PointSelector
import me.anno.maths.Packing.pack64
import org.joml.Vector2f
import speiger.primitivecollections.LongToObjectHashMap

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
        val newCorners = Array(corners.size) { Vector2f(0f, 0f) }

        for (q in corners) {
            newCorners[q.index] = if (q.border) q.point else {
                val avg = Vector2f(0f, 0f)
                for (r in q.touches) {
                    avg.x += r.point.x
                    avg.y += r.point.y
                }
                avg.x /= q.touches.size
                avg.y /= q.touches.size
                avg
            }
        }

        for (i in corners.indices) {
            corners[i].point.set(newCorners[i])
        }
    }

    private fun buildGraph(points: FloatArray, voronoi: Voronoi, size: Float): Graph {

        // Create Center objects for each point
        val centers = (0 until points.size / 2).map { Center(Vector2f(points, it * 2)) }

        // Helper: create or reuse Corner object
        val corners = ArrayList<Corner>()
        val cornerMap = LongToObjectHashMap<Corner>()
        fun makeCorner(px: Float, py: Float): Corner? {
            if (px.isNaN() || py.isNaN()) return null
            val key = pack64(px.toRawBits(), py.toRawBits())
            return cornerMap.getOrPut(key) {
                val corner = Corner(corners.size, Vector2f(px, py))
                corner.border = px <= 0f || px >= size || py <= 0f || py >= size
                corners.add(corner)
                corner
            }
        }

        fun addIfNotPresent(list: ArrayList<Center>, c: Center?) {
            if (c != null && !list.contains(c)) list.add(c)
        }

        fun addIfNotPresent(list: ArrayList<Corner>, c: Corner?) {
            if (c != null && !list.contains(c)) list.add(c)
        }

        // Build edges
        val edges0 = voronoi.edges
        val edges = EdgeList(edges0.size)

        for (edgeIndex in 0 until edges0.size) {
            val d0 = centers.getOrNull(edges0.getRegionL(edgeIndex))
            val d1 = centers.getOrNull(edges0.getRegionR(edgeIndex))

            val v0 = makeCorner(edges0.getVertex(edgeIndex, 0), edges0.getVertex(edgeIndex, 1))
            val v1 = makeCorner(edges0.getVertex(edgeIndex, 2), edges0.getVertex(edgeIndex, 3))

            edges.setV0(edgeIndex, v0)
            edges.setV1(edgeIndex, v1)

            // Centers point to edges
            d0?.borders?.add(edgeIndex)
            d1?.borders?.add(edgeIndex)

            // Corners point to edges
            v0?.protrudes?.add(edgeIndex)
            v1?.protrudes?.add(edgeIndex)

            // Centers point to centers
            if (d0 != null && d1 != null) {
                addIfNotPresent(d0.neighbors, d1)
                addIfNotPresent(d1.neighbors, d0)
            }

            // Corners point to corners
            if (v0 != null && v1 != null) {
                addIfNotPresent(v0.adjacent, v1)
                addIfNotPresent(v1.adjacent, v0)
            }

            // Centers point to corners
            d0?.let { addIfNotPresent(it.corners, v0); addIfNotPresent(it.corners, v1) }
            d1?.let { addIfNotPresent(it.corners, v0); addIfNotPresent(it.corners, v1) }

            // Corners point to centers
            v0?.let { addIfNotPresent(it.touches, d0); addIfNotPresent(it.touches, d1) }
            v1?.let { addIfNotPresent(it.touches, d0); addIfNotPresent(it.touches, d1) }
        }

        return Graph(centers, corners, edges)
    }

}