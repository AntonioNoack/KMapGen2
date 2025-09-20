package amitp.mapgen2.graphbuilder.voronoi

import amitp.mapgen2.structures.VEdgeList
import me.anno.graph.octtree.QuadTreeF
import me.anno.maths.Packing.pack64
import me.anno.maths.Packing.unpackHighFrom64
import me.anno.maths.Packing.unpackLowFrom64
import org.joml.Vector2f
import speiger.primitivecollections.LongToIntHashMap
import speiger.primitivecollections.LongToLongHashMap
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/** ---------- Voronoi implementation using Bowyer-Watson Delaunay ----------
 *
 * Produces Delaunay triangles, computes circumcenters and uses them to
 * form Voronoi vertices and Voronoi edges. This is not a high-performance
 * computational-geometry library, but it is robust enough for map generation.
 *
 * Usage:
 *   val vor = Voronoi(points, size)
 *   val vedges = vor.edges   // list of VEdge
 *   val region = vor.region(sitePoint)   // polygon vertices for that site
 *
 * VEdge.regionL / regionR are indices into the original sites list (or null).
 * VEdge.vertexA / vertexB are circumcenters (Points) - may be null for hull edges.
 */
class Voronoi(points: FloatArray, bboxSize: Vector2f) {

    companion object {
        private val nan = Vector2f(Float.NaN)
    }

    // Public results
    val edges: VEdgeList
    private val triangles: List<Triangle>

    init {
        // Build Delaunay triangulation using Bowyer-Watson
        val (tris, triAdj) = buildDelaunay(points, bboxSize)
        triangles = tris

        // Compute circumcenters (Voronoi vertices) for every triangle
        val circumcenters = triangles.map { it.circumcenter() }

        // Build map from triangle edges to adjacent triangles (triAdj)
        // triAdj: map of undirected edge key to list of triangle indices touching it
        val vEdges = VEdgeList(triAdj.size)

        // For each undirected edge between two sites, there may be 1 or 2 triangles.
        // We inspect triAdj to produce a Voronoi edge between the circumcenters of
        // the adjacent triangles. If only one triangle (hull edge), we'll produce a
        // VEdge with one endpoint and the other null.
        var index = 0
        triAdj.forEach { edgeKey, triIndices ->
            // edgeKey is "i-j" where i<j are site indices
            val i = unpackHighFrom64(edgeKey)
            val j = unpackLowFrom64(edgeKey)

            val t0 = unpackHighFrom64(triIndices)
            val t1 = unpackLowFrom64(triIndices)

            val c0 = circumcenters.getOrNull(t0)
            val c1 = circumcenters.getOrNull(t1)

            // regionL / regionR are the two sites that share the Delaunay edge
            vEdges.setRegionL(index, i)
            vEdges.setRegionR(index, j)
            vEdges.setVertices(index, c0 ?: nan, c1 ?: nan)
            index++
        }

        edges = vEdges
    }

    // -------------------- Internal Delaunay structures --------------------

    // Triangle stores indices into 'sites'
    data class Triangle(
        val a: Int,
        val b: Int,
        val c: Int,
        val circumcircleX: Float,
        val circumcircleY: Float,
        val circumcircleRadiusSq: Float
    ) {

        val min: Vector2f
        val max: Vector2f

        init {
            val radius = sqrt(circumcircleRadiusSq)
            min = Vector2f(circumcircleX, circumcircleY).sub(radius, radius)
            max = Vector2f(circumcircleX, circumcircleY).add(radius, radius)
        }

        // precomputed circumcircle cell (sx, sy) and radius squared (sRadiusSq)
        // constructor factory
        companion object {

            fun of(a: Int, b: Int, c: Int, pts: FloatArray): Triangle {
                val pax = pts[a * 2]
                val pay = pts[a * 2 + 1]
                val cc = circumcenter(pax, pay, pts[b * 2], pts[b * 2 + 1], pts[c * 2], pts[c * 2 + 1])
                val dx = cc.x - pax
                val dy = cc.y - pay
                val r2 = dx * dx + dy * dy
                return Triangle(a, b, c, cc.x, cc.y, r2)
            }

            fun circumcenter(
                px: Float, py: Float,
                qx: Float, qy: Float,
                rx: Float, ry: Float,
                dst: Vector2f = Vector2f()
            ): Vector2f {
                // compute circumcenter via coordinates
                val A = qx - px
                val B = qy - py
                val C = rx - px
                val D = ry - py
                val E = A * (px + qx) + B * (py + qy)
                val F = C * (px + rx) + D * (py + ry)
                val G = 2f * (A * (ry - qy) - B * (rx - qx))
                return if (abs(G) < 1e-12f) {
                    // Collinear or nearly; fallback to large circumcenter far away
                    dst.set((px + qx + rx) / 3f, (py + qy + ry) / 3f)
                } else {
                    dst.set((D * E - B * F) / G, (A * F - C * E) / G)
                }
            }
        }

        fun isEndIndex(idx: Int): Boolean = a >= idx || b >= idx || c >= idx
        fun circumcenter(): Vector2f = Vector2f(circumcircleX, circumcircleY)
        fun circumcircleContains(pt: Vector2f): Boolean {
            val dx = circumcircleX - pt.x
            val dy = circumcircleY - pt.y
            return dx * dx + dy * dy <= circumcircleRadiusSq + 1e-12
        }
    }

    // Build Delaunay triangulation: returns (triangles, adjacency map)
    private fun buildDelaunay(points: FloatArray, bbox: Vector2f):
            Pair<List<Triangle>, LongToLongHashMap> {

        if (points.size < 3) return Pair(emptyList(), LongToLongHashMap(-1))

        val triangleLookup = TriangleLookup()

        // Super-triangle: big triangle that contains all points
        val sx = bbox.x * 100f // large enough
        val sy = bbox.y * 100f // large enough

        // We'll operate on an indexed points array: original pts plus super-triangle vertices
        val allPts = points.copyOf(points.size + 6)
        // choose triangle vertices as three points outside bounding box
        allPts[points.size] = -sx
        allPts[points.size + 1] = -sy
        allPts[points.size + 2] = sx
        allPts[points.size + 3] = -sy
        allPts[points.size + 4] = 0f
        allPts[points.size + 5] = sy * 2f

        val idxA = points.size shr 1

        // initial triangle
        val superTri = Triangle.of(idxA, idxA + 1, idxA + 2, allPts)
        triangleLookup.add(superTri)

        // Bowyer-Watson incremental insertion
        val badTriangles = ArrayList<Triangle>()
        val edgeCount = LongToIntHashMap(0)

        val shuffledIndices = IntArray(points.size shr 1) { it }
        shuffledIndices.shuffle()

        val p = Vector2f()
        for (i in shuffledIndices.indices) {
            val i2 = shuffledIndices[i] * 2

            // 1) find all triangles whose circumcircle contains p
            p.set(points, i2)
            triangleLookup.query(p, p) { tri ->
                if (tri.circumcircleContains(p)) {
                    badTriangles.add(tri)
                }
                false
            }

            // 2) find polygon hole boundary (edges that are not shared by two bad triangles)
            // represent edge as pair (min,max) string
            for (ti in badTriangles.indices) {
                val tri = badTriangles[ti]
                val key0 = orderedKey(tri.a, tri.b)
                val key1 = orderedKey(tri.b, tri.c)
                val key2 = orderedKey(tri.c, tri.a)

                edgeCount[key0]++
                edgeCount[key1]++
                edgeCount[key2]++
            }

            // 3) remove bad triangles
            for (bad in badTriangles) {
                triangleLookup.remove(bad)
            }

            // 4) re-triangulate the polygonal hole by connecting p to each boundary edge
            edgeCount.forEach { key, value ->
                if (value == 1) {
                    val a = unpackHighFrom64(key)
                    val b = unpackLowFrom64(key)
                    triangleLookup.add(Triangle.of(a, b, i2 shr 1, allPts))
                }
            }

            badTriangles.clear()
            edgeCount.clear()
        }

        // Remove triangles that use super-triangle vertices
        val triangleList = ArrayList<Triangle>(triangleLookup.size)
        triangleLookup.forEach { tri ->
            if (!tri.isEndIndex(idxA)) {
                triangleList.add(tri)
            }
        }

        // Build adjacency: map each undirected site-edge (i-j) to list of triangle indices that touch it
        val edgeToTriangles = LongToLongHashMap(pack64(-1, -1))

        fun addTriangleToEdge(ti: Int, edgeA: Int, edgeB: Int) {
            val edgeKey = orderedKey(edgeA, edgeB)
            val index = edgeToTriangles.findIndex(edgeKey)
            if (index < 0) {
                // not yet known
                edgeToTriangles[edgeKey] = pack64(-1, ti)
            } else {
                // one value already exists, add the other one
                val values = edgeToTriangles.values
                val oldTi = unpackLowFrom64(values[index])
                values[index] = pack64(oldTi, ti)
            }
        }

        for (ti in triangleList.indices) {
            val tri = triangleList[ti]
            addTriangleToEdge(ti, tri.a, tri.b)
            addTriangleToEdge(ti, tri.b, tri.c)
            addTriangleToEdge(ti, tri.c, tri.a)
        }

        return Pair(triangleList, edgeToTriangles)
    }

    // todo why is this scaling soo badly that we need a 512 size for optimal performance???
    private class TriangleLookup : QuadTreeF<Triangle>(1024) {
        override fun createChild() = TriangleLookup()
        override fun getMin(data: Triangle): Vector2f = data.min
        override fun getMax(data: Triangle): Vector2f = data.max
    }

    private fun orderedKey(i: Int, j: Int): Long {
        return pack64(min(i, j), max(i, j))
    }
}