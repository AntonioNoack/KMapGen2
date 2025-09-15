package amitp.mapgen2

import amitp.mapgen2.Biome.Companion.getBiome
import amitp.mapgen2.geometry.Center
import amitp.mapgen2.geometry.Corner
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
        assignCornerElevations(islandShape, graph.corners, size)
        assignOceanCoastAndLand(graph.centers, graph.corners)
        redistributeElevations(landCorners(graph.corners))
        for (q in graph.corners) {
            if (q.ocean || q.coast) q.elevation = 0f
        }
        assignPolygonElevations(graph.centers)
    }

    fun assignMoisture(graph: Graph, mapRandom: Random) {
        calculateDownslopes(graph.corners)
        calculateWatersheds(graph.corners)
        createRivers(graph.corners, graph.edges, graph.centers.size, mapRandom)
        assignCornerMoisture(graph.corners)
        redistributeMoisture(landCorners(graph.corners))
        assignPolygonMoisture(graph.centers)
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

    fun assignCornerElevations(islandShape: IslandShape, corners: List<Corner>, size: Float) {
        val queue: ArrayDeque<Corner> = ArrayDeque()

        // Initialize border corners
        val invSize = 1f / size
        for (i in corners.indices) {
            val c = corners[i]
            c.water = !inside(islandShape, invSize, c.point)
            if (c.border) {
                c.elevation = 0f
                queue.addLast(c)
            } else {
                c.elevation = Float.MAX_VALUE
            }
        }

        // BFS flood-fill
        while (queue.isNotEmpty()) {
            val q = queue.removeFirst()
            for (s in q.adjacent) {
                // Compute new elevation candidate
                var newElevation = q.elevation + 0.01f
                if (q.water == s.water) {
                    // If both same type (land–land or water–water), small increment
                    newElevation = q.elevation + 1f
                    // avoid lakes being lower than surrounding ocean
                    newElevation = max(newElevation, 0f)
                }
                if (newElevation < s.elevation - 0.01f) {
                    s.elevation = newElevation
                    queue.addLast(s)
                }
            }
        }
    }

    fun assignOceanCoastAndLand(centers: List<Center>, corners: List<Corner>) {
        val queue: ArrayDeque<Center> = ArrayDeque()

        // Step 1: Mark border polygons as ocean
        for (p in centers) {
            var numWater = 0
            for (q in p.corners) {
                if (q.border) {
                    p.border = true
                    p.ocean = true
                    q.water = true
                    queue.addLast(p)
                }
                if (q.water) numWater++
            }
            // Mark lakes
            p.water = p.ocean || numWater >= p.corners.size * LAKE_THRESHOLD
        }

        // Step 2: Flood-fill ocean from borders
        while (queue.isNotEmpty()) {
            val p = queue.removeFirst()
            for (r in p.neighbors) {
                if (r.water && !r.ocean) {
                    r.ocean = true
                    queue.addLast(r)
                }
            }
        }

        // Step 3: Mark coasts for polygons
        for (p in centers) {
            var numOcean = 0
            var numLand = 0
            for (r in p.neighbors) {
                if (r.ocean) numOcean++
                if (!r.water) numLand++
            }
            p.coast = numOcean > 0 && numLand > 0
        }

        // Step 4: Update corners
        for (q in corners) {
            var numOcean = 0
            var numLand = 0
            for (p in q.touches) {
                if (p.ocean) numOcean++
                if (!p.water) numLand++
            }
            q.ocean = (numOcean == q.touches.size)
            q.coast = (numOcean > 0) && (numLand > 0)
            q.water = q.border || ((numLand != q.touches.size) && !q.coast)
        }
    }

    fun assignPolygonElevations(centers: List<Center>) {
        for (p in centers) {
            var sumElevation = 0f
            for (q in p.corners) {
                sumElevation += q.elevation
            }
            p.elevation = sumElevation / p.corners.size
        }
    }

    fun calculateDownslopes(corners: List<Corner>) {
        for (q in corners) {
            var lowest = q
            for (s in q.adjacent) {
                if (s.elevation <= lowest.elevation) {
                    lowest = s
                }
            }
            q.downslope = lowest
        }
    }

    fun calculateWatersheds(corners: List<Corner>) {
        // Step 1: Initialize watershed pointers
        for (q in corners) {
            q.watershed = if (!q.ocean && !q.coast) q.downslope else q
            q.watershedSize = 0
        }

        // Step 2: Iteratively propagate watersheds
        repeat(100) {
            var changed = false
            for (q in corners) {
                if (!q.ocean && !q.coast && !(q.watershed?.coast ?: false)) {
                    val r = q.downslope?.watershed
                    if (r != null && !r.ocean && q.watershed != r) {
                        q.watershed = r
                        changed = true
                    }
                }
            }
            if (!changed) return@repeat
        }

        // Step 3: Count watershed sizes
        for (q in corners) {
            q.watershed?.watershedSize = (q.watershed?.watershedSize ?: 0) + 1
        }
    }

    fun createRivers(corners: List<Corner>, edges: EdgeList, numPoints: Int, mapRandom: Random) {
        val maxAttempts = numPoints / 10
        repeat(maxAttempts) {
            val q = corners[mapRandom.nextInt(corners.size)]
            // Skip unsuitable corners
            if (q.ocean || q.coast || q.elevation < 0.3 || q.elevation > 0.9) return@repeat

            var current: Corner = q
            while (!current.coast && !current.ocean) {
                if (current == current.downslope) break // stuck

                val edge = lookupEdgeFromCorner(current, current.downslope!!, edges)
                if (edge != -1) {
                    edges.addRiver(edge)
                    current.river++
                    current.downslope!!.river++
                }
                current = current.downslope ?: continue
            }
        }
    }

    fun assignCornerMoisture(corners: List<Corner>) {
        val queue = ArrayDeque<Corner>()

        // Step 1: Initialize moisture and enqueue freshwater sources
        for (q in corners) {
            if ((q.water || q.river > 0) && !q.ocean) {
                q.moisture = if (q.river > 0) {
                    min(3f, 0.2f * q.river)
                } else {
                    1f
                }
                queue.addLast(q)
            } else {
                q.moisture = 0f
            }
        }

        // Step 2: BFS spread to adjacent corners
        while (queue.isNotEmpty()) {
            val q = queue.removeFirst()
            for (r in q.adjacent) {
                val newMoisture = q.moisture * 0.9f
                if (newMoisture > r.moisture) {
                    r.moisture = newMoisture
                    queue.addLast(r)
                }
            }
        }

        // Step 3: Saltwater corners get full moisture
        for (q in corners) {
            if (q.ocean || q.coast) {
                q.moisture = 1f
            }
        }
    }

    fun assignPolygonMoisture(centers: List<Center>) {
        for (p in centers) {
            var sumMoisture = 0f
            for (q in p.corners) {
                if (q.moisture > 1.0) q.moisture = 1f
                sumMoisture += q.moisture
            }
            p.moisture = sumMoisture / p.corners.size
        }
    }

    fun assignBiomes(centers: List<Center>) {
        for (p in centers) {
            p.biome = getBiome(p)
        }
    }

    fun lookupEdgeFromCorner(
        q: Corner, s: Corner,
        edges: EdgeList
    ): Int {
        val edges1 = q.protrudes
        val s = s.index
        for (index in 0 until edges1.size) {
            val edgeIndex = edges1[index]
            val v0 = edges.getV0(edgeIndex)
            val v1 = edges.getV1(edgeIndex)
            if (v0 == s || v1 == s) return edgeIndex
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
