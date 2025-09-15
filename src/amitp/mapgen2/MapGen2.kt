package amitp.mapgen2

import amitp.mapgen2.Biome.Companion.getBiome
import amitp.mapgen2.geometry.CenterList
import amitp.mapgen2.geometry.Corner
import amitp.mapgen2.geometry.CornerList
import amitp.mapgen2.geometry.CornerList.Companion.toCornerList
import amitp.mapgen2.geometry.CornerList.Companion.toCorners
import amitp.mapgen2.geometry.EdgeList
import amitp.mapgen2.graph.Graph
import amitp.mapgen2.graph.GraphBuilder
import amitp.mapgen2.shape.IslandShape
import me.anno.utils.Clock
import org.apache.logging.log4j.LogManager
import org.joml.Vector2f
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt
import kotlin.random.Random

object MapGen2 {

    private val LOGGER = LogManager.getLogger(MapGen2::class)
    const val LAKE_THRESHOLD = 0.3

    fun buildGraph(
        graphBuilder: GraphBuilder, numPoints: Int,
        mapRandom: Random, size: Float,
    ): Graph = graphBuilder.buildGraph(size, numPoints, mapRandom.nextLong())

    fun assignElevations(islandShape: IslandShape, graph: Graph, size: Float) {
        assignCornerElevations(islandShape, graph.cornerList, size)
        assignOceanCoastAndLand(graph.centers, graph.cornerList)

        graph.cornerList.toCorners(graph.corners)
        redistributeElevations(landCorners(graph.corners))
        graph.corners.toCornerList(graph.cornerList)

        clearOceanElevation(graph.cornerList)
        assignPolygonElevations(graph.centers, graph.cornerList)
    }

    fun clearOceanElevation(corners: CornerList) {
        for (q in 0 until corners.size) {
            if (corners.isOcean(q) || corners.isCoast(q)) {
                corners.setElevation(q, 0f)
            }
        }
    }

    fun assignMoisture(graph: Graph, mapRandom: Random) {
        calculateDownslopes(graph.cornerList)
        calculateWatersheds(graph.cornerList)
        createRivers(graph.cornerList, graph.edges, graph.centers.size, mapRandom)
        assignCornerMoisture(graph.cornerList)
        graph.cornerList.toCorners(graph.corners)

        redistributeMoisture(landCorners(graph.corners))

        graph.corners.toCornerList(graph.cornerList)
        assignPolygonMoisture(graph.centers, graph.cornerList)
        graph.cornerList.toCorners(graph.corners)
    }

    fun generate(
        size: Float,
        islandShape: IslandShape,
        graphBuilder: GraphBuilder,
        numPoints: Int,
        variant: Long
    ): Graph {
        val mapRandom = Random(variant)

        val clock = Clock(LOGGER)
        val graph = buildGraph(graphBuilder, numPoints, mapRandom, size)
        clock.stop("Build Graph")

        assignElevations(islandShape, graph, size)
        clock.stop("Assign Elevations")

        assignMoisture(graph, mapRandom)
        clock.stop("Assign Moisture")

        assignBiomes(graph.centers)
        clock.stop("Assign Biomes")

        return graph
    }

    fun landCorners(corners: List<Corner>): List<Corner> {
        return corners.filter { q -> !q.ocean && !q.coast }
    }

    fun assignCornerElevations(islandShape: IslandShape, corners: CornerList, size: Float) {
        val queue: ArrayDeque<Int> = ArrayDeque()

        // Initialize border corners
        val invSize = 1f / size
        for (c in 0 until corners.size) {
            corners.setWater(
                c, !inside(
                    islandShape, invSize,
                    Vector2f(corners.getPointX(c), corners.getPointY(c))
                )
            )

            if (corners.isBorder(c)) {
                corners.setElevation(c, 0f)
                queue.addLast(c)
            } else {
                corners.setElevation(c, Float.MAX_VALUE)
            }
        }

        // BFS flood-fill
        while (queue.isNotEmpty()) {
            val q = queue.removeFirst()
            corners.adjacent.forEach(q) { s ->
                // Compute new elevation candidate
                var newElevation = corners.getElevation(q) + 0.01f
                if (corners.isWater(q) == corners.isWater(s)) {
                    // If both same type (land–land or water–water), small increment
                    newElevation = corners.getElevation(q) + 1f
                    // avoid lakes being lower than surrounding ocean
                    newElevation = max(newElevation, 0f)
                }
                if (newElevation < corners.getElevation(s) - 0.01f) {
                    corners.setElevation(s, newElevation)
                    queue.addLast(s)
                }
            }
        }
    }

    fun assignOceanCoastAndLand(centers: CenterList, corners: CornerList) {
        // todo probably could use a more efficient data structure without encapsulation
        val queue = ArrayDeque<Int>()

        // Step 1: Mark border polygons as ocean
        for (ci in 0 until centers.size) {
            var numWater = 0
            centers.corners.forEach(ci) { q ->
                if (corners.isBorder(q)) {
                    centers.setOcean(ci, true)
                    centers.setBorder(ci, true)
                    corners.setWater(q, true)
                    queue.addLast(ci)
                }
                if (corners.isWater(q)) numWater++
            }
            // Mark lakes
            centers.setWater(
                ci, centers.isOcean(ci) ||
                        numWater >= centers.corners.getSize(ci) * LAKE_THRESHOLD
            )
        }

        // Step 2: Flood-fill ocean from borders
        while (queue.isNotEmpty()) {
            val ci = queue.removeFirst()
            centers.neighbors.forEach(ci) { ni ->
                if (centers.isWater(ni) && !centers.isOcean(ni)) {
                    centers.setOcean(ni, true)
                    queue.addLast(ni)
                }
            }
        }

        // Step 3: Mark coasts for polygons
        for (ci in 0 until centers.size) {
            var numOcean = 0
            var numLand = 0
            centers.neighbors.forEach(ci) { r ->
                if (centers.isOcean(r)) numOcean++
                if (!centers.isWater(r)) numLand++
            }
            centers.setCoast(ci, numOcean > 0 && numLand > 0)
        }

        // Step 4: Update corners
        for (q in 0 until corners.size) {
            var numOcean = 0
            var numLand = 0
            val numTouches = corners.touches.forEach(q) { p ->
                if (centers.isOcean(p)) numOcean++
                if (!centers.isWater(p)) numLand++
            }
            corners.setOcean(q, numOcean == numTouches)
            corners.setCoast(q, numOcean > 0 && numLand > 0)
            corners.setWater(
                q, corners.isBorder(q) ||
                        ((numLand != numTouches) && !corners.isCoast(q))
            )
        }
    }

    fun assignPolygonElevations(centers: CenterList, corners: CornerList) {
        for (p in 0 until centers.size) {
            var sumElevation = 0f
            val size = centers.corners.forEach(p) { q ->
                sumElevation += corners.getElevation(q)
            }
            centers.setElevation(p, sumElevation / size)
        }
    }

    fun calculateDownslopes(corners: CornerList) {
        for (q in 0 until corners.size) {
            var lowest = q
            corners.adjacent.forEach(q) { s ->
                if (corners.getElevation(s) <= corners.getElevation(lowest)) {
                    lowest = s
                }
            }
            corners.setDownslope(q, lowest)
        }
    }

    fun calculateWatersheds(corners: CornerList) {
        // Step 1: Initialize watershed pointers
        for (q in 0 until corners.size) {
            val watershed = if (!corners.isOcean(q) && !corners.isCoast(q)) corners.getDownslope(q) else q
            corners.setWatershed(q, watershed)
            corners.setWatershedSize(q, 0)
        }

        // Step 2: Iteratively propagate watersheds
        repeat(100) {
            var changed = false
            for (q in 0 until corners.size) {
                if (!corners.isOcean(q) && !corners.isCoast(q)) {
                    val watershed = corners.getWatershed(q)
                    if (watershed >= 0 && !corners.isCoast(watershed)) {
                        val downslope = corners.getDownslope(q)
                        if (downslope < 0) continue
                        val r = corners.getWatershed(downslope)
                        if (r >= 0 && !corners.isOcean(r) && watershed != r) {
                            corners.setWatershed(q, r)
                            changed = true
                        }
                    }
                }
            }
            if (!changed) return@repeat
        }

        // Step 3: Count watershed sizes
        for (q in 0 until corners.size) {
            val watershed = corners.getWatershed(q)
            if (watershed < 0) continue
            corners.setWatershedSize(watershed, corners.getWatershedSize(watershed) + 1)
        }
    }

    fun createRivers(corners: CornerList, edges: EdgeList, numPoints: Int, mapRandom: Random) {
        val maxAttempts = numPoints / 10
        repeat(maxAttempts) {
            val q = mapRandom.nextInt(corners.size)
            // Skip unsuitable corners
            if (corners.isOcean(q) ||
                corners.isCoast(q) ||
                corners.getElevation(q) !in 0.3f..0.9f
            ) return@repeat

            var current = q
            while (!corners.isCoast(current) && !corners.isOcean(current)) {
                if (current == corners.getDownslope(current)) break // stuck

                val edge = lookupEdgeFromCorner(corners, current, corners.getDownslope(current), edges)
                if (edge != -1) {
                    edges.addRiver(edge)
                    corners.addRiver(current)
                    corners.addRiver(corners.getDownslope(current))
                }
                current = corners.getDownslope(current)
            }
        }
    }

    fun assignCornerMoisture(corners: CornerList) {
        // todo use data-structure without boxing
        val queue = ArrayDeque<Int>()

        // Step 1: Initialize moisture and enqueue freshwater sources
        for (q in 0 until corners.size) {
            if ((corners.isWater(q) || corners.getRiver(q) > 0) && !corners.isOcean(q)) {
                val moisture = if (corners.getRiver(q) > 0) {
                    min(3f, 0.2f * corners.getRiver(q))
                } else 1f
                corners.setMoisture(q, moisture)
                queue.addLast(q)
            } else {
                corners.setMoisture(q, 0f)
            }
        }

        // Step 2: BFS spread to adjacent corners
        while (queue.isNotEmpty()) {
            val q = queue.removeFirst()
            corners.adjacent.forEach(q) { r ->
                val newMoisture = corners.getMoisture(q) * 0.9f
                if (newMoisture > corners.getMoisture(r)) {
                    corners.setMoisture(r, newMoisture)
                    queue.addLast(r)
                }
            }
        }

        // Step 3: Saltwater corners get full moisture
        for (q in 0 until corners.size) {
            if (corners.isOcean(q) || corners.isCoast(q)) {
                corners.setMoisture(q, 1f)
            }
        }
    }

    fun assignPolygonMoisture(centers: CenterList, corners: CornerList) {
        for (ci in 0 until centers.size) {
            var sumMoisture = 0f
            val size = centers.corners.forEach(ci) { q ->
                if (corners.getMoisture(q) > 1.0) corners.setMoisture(q, 1f)
                sumMoisture += corners.getMoisture(q)
            }
            centers.setMoisture(ci, sumMoisture / size)
        }
    }

    fun assignBiomes(centers: CenterList) {
        for (p in 0 until centers.size) {
            centers.setBiome(p, getBiome(centers, p))
        }
    }

    fun lookupEdgeFromCorner(corners: CornerList, q: Int, sIndex: Int, edges: EdgeList): Int {
        corners.protrudes.forEach(q) { edgeIndex ->
            val v0 = edges.getV0(edgeIndex)
            val v1 = edges.getV1(edgeIndex)
            if (v0 == sIndex || v1 == sIndex) return edgeIndex
        }
        return -1
    }

    fun inside(islandShape: IslandShape, invSize: Float, p: Vector2f): Boolean {
        val nx = p.x * invSize * 2f - 1f
        val ny = p.y * invSize * 2f - 1f
        return islandShape.isOnLand(nx, ny)
    }

    fun redistributeElevations(locations: List<Corner>) {
        val SCALE_FACTOR = 1.1f

        // Sort corners by elevation ascending
        val sorted = locations.sortedBy { it.elevation }

        val n = sorted.size
        for ((i, corner) in sorted.withIndex()) {
            val y = i.toFloat() / (n - 1).coerceAtLeast(1)
            // Solve x^2 - 2x + y = 0 -> x = 1 - sqrt(1 - y)
            var x = sqrt(SCALE_FACTOR) - sqrt(SCALE_FACTOR * (1f - y))
            if (x > 1f) x = 1f
            corner.elevation = x
        }
    }

    fun redistributeMoisture(locations: List<Corner>) {
        // Sort corners by moisture ascending
        val sorted = locations.sortedBy { it.moisture }

        val n = sorted.size
        for ((i, corner) in sorted.withIndex()) {
            corner.moisture = i.toFloat() / (n - 1).coerceAtLeast(1)
        }
    }

}
