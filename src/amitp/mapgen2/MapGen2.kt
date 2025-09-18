package amitp.mapgen2

import amitp.mapgen2.Biome.Companion.getBiome
import amitp.mapgen2.graphbuilder.GraphBuilder
import amitp.mapgen2.islandshapes.IslandShape
import amitp.mapgen2.structures.CellList
import amitp.mapgen2.structures.CornerList
import amitp.mapgen2.structures.EdgeList
import me.anno.maths.Maths.clamp
import me.anno.maths.Maths.mix
import me.anno.utils.Clock
import me.anno.utils.algorithms.Sortable
import me.anno.utils.structures.arrays.IntArrayList
import org.joml.Vector2f
import speiger.primitivecollections.callbacks.IntPredicate
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt
import kotlin.random.Random

object MapGen2 {

    const val LAKE_THRESHOLD = 0.3

    fun generate(
        size: Float,
        islandShape: IslandShape,
        graphBuilder: GraphBuilder,
        numPoints: Int,
        variant: Long
    ): GeneratedMap {
        val mapRandom = Random(variant)

        val clock = Clock("MapGen2")
        val map = buildGraph(graphBuilder, numPoints, mapRandom, size)
        clock.stop("Build Graph")

        assignElevations(islandShape, map, size)
        clock.stop("Assign Elevations")

        assignMoisture(map, mapRandom)
        clock.stop("Assign Moisture")

        assignBiomes(map.cells)
        clock.stop("Assign Biomes")

        flattenLakes(map, Biome.LAKE, 0.02f)
        clock.stop("Flatten Lakes")

        flattenBeaches(map)
        clock.stop("Flatten Beaches")

        flattenLines(map, true) { edge -> map.edges.hasRiver(edge) }
        clock.stop("Flatten Rivers")

        sortElementsByAngle(map)
        clock.stop("Sort by Angle")

        return map
    }

    fun sortElementsByAngle(map: GeneratedMap) {
        val cells = map.cells
        val corners = map.corners
        val edges = map.edges
        // sort all cell properties
        cells.sortByAngle(cells.neighbors, cells)
        cells.sortByAngle(cells.corners, corners)
        cells.sortByAngle(cells.edges, edges, corners)
        // sort all corner properties
        corners.sortByAngle(corners.cells, cells)
        corners.sortByAngle(corners.neighbors, corners)
        corners.sortByAngle(corners.edges, edges, corners)
        // edges don't store any references
    }

    fun buildGraph(graphBuilder: GraphBuilder, numPoints: Int, mapRandom: Random, size: Float): GeneratedMap =
        graphBuilder.buildGraph(size, numPoints, mapRandom.nextLong())

    fun assignElevations(islandShape: IslandShape, graph: GeneratedMap, size: Float) {
        assignCornerElevations(islandShape, graph.corners, size)
        assignOceanCoastAndLand(graph.cells, graph.corners)
        redistributeElevations(graph.corners, landCorners(graph.corners))
        clearOceanElevation(graph.corners)
        assignPolygonElevations(graph.cells, graph.corners)
    }

    fun clearOceanElevation(corners: CornerList) {
        for (q in corners.indices) {
            if (corners.isOcean(q) || corners.isCoast(q)) {
                corners.setElevation(q, 0f)
            }
        }
    }

    fun assignMoisture(map: GeneratedMap, mapRandom: Random) {
        calculateDownslopes(map.corners)
        calculateWatersheds(map.corners)
        createRivers(map.corners, map.edges, map.cells.size, mapRandom)
        assignCornerMoisture(map.corners)
        redistributeMoisture(map.corners, landCorners(map.corners))
        assignPolygonMoisture(map.cells, map.corners)
    }

    fun flattenLakes(
        map: GeneratedMap, lakeBiome: Biome,
        maxDeltaElevation: Float
    ) {
        val cells = map.cells
        val corners = map.corners

        var genId = 10
        val done = IntArray(cells.size)
        val queue = IntArrayList()

        for (i in cells.indices) {

            fun findLake(ci: Int, generation: Int, callback: (Int) -> Unit) {
                if (done[ci] == generation) return
                done[ci] = generation
                callback(ci)

                cells.neighbors.forEach(ci) { qi ->
                    if (cells.getBiome(qi) == lakeBiome &&
                        done[qi] != generation
                    ) findLake(qi, generation, callback)
                }
            }

            if (cells.getBiome(i) == lakeBiome && done[i] == 0) {
                // we found a new lake -> flatten it
                var minElevation = cells.getElevation(i)
                findLake(i, genId++) { lakeCell ->
                    minElevation = min(minElevation, cells.getElevation(lakeCell))
                }
                findLake(i, genId++) { lakeCell ->
                    cells.setElevation(lakeCell, minElevation)
                    cells.corners.forEach(lakeCell) { corner ->
                        val prevElevation = 0f
                        corners.setElevation(corner, minElevation)
                        if (abs(prevElevation - minElevation) > maxDeltaElevation) {
                            queue.add(corner)
                        }
                    }
                }
                flattenTerrainAroundCorners(map, queue, maxDeltaElevation, done)
            }
        }
    }

    fun flattenBeaches(map: GeneratedMap) {
        val cells = map.cells
        val corners = map.corners

        val done = IntArray(cells.size)
        val queue = IntArrayList()
        val maxDeltaElevation = 0.17f // good measure???

        for (i in cells.indices) {
            if (done[i] != 0) continue
            if (cells.getBiome(i) == Biome.OCEAN) continue

            cells.corners.forEach(i) { corner ->
                if (corners.getElevation(corner) == 0f) {
                    queue.add(corner)
                }
            }
        }

        flattenTerrainAroundCorners(map, queue, maxDeltaElevation, done)
    }

    /**
     * this algorithm is a little broken, but it looks nice ^^
     * */
    fun flattenTerrainAroundCorners(
        map: GeneratedMap, queue: IntArrayList,
        maxDeltaElevation: Float, done: IntArray
    ) {

        val cells = map.cells
        val corners = map.corners

        // smooth the terrain around
        while (queue.isNotEmpty()) {
            val corner = queue.removeLast()
            val elevation = corners.getElevation(corner)

            corners.neighbors.forEach(corner) { neighbor ->
                val prevElevation = corners.getElevation(neighbor)
                val delta = prevElevation - elevation
                if (abs(delta) > maxDeltaElevation) {
                    var hasLake = false
                    corners.cells.forEach(neighbor) { cellI ->
                        val biome = cells.getBiome(cellI)
                        if (biome == Biome.LAKE ||
                            biome == Biome.LAVA ||
                            biome == Biome.OCEAN
                        ) hasLake = true
                    }
                    if (!hasLake) {

                        // fix elevation
                        val newElevation = elevation + clamp(delta, -maxDeltaElevation, maxDeltaElevation)
                        corners.setElevation(neighbor, newElevation)

                        val delta = newElevation - prevElevation
                        var anyCellChanged = false
                        corners.cells.forEach(neighbor) { cellI ->
                            // adjust elevation of bordering cells if they are not done yet
                            if (done[cellI] < 9 && !cells.isOcean(cellI)) {
                                val numCornersOnCell = cells.corners.getSize(cellI)
                                if (numCornersOnCell > 0) {
                                    val newElevationI = cells.getElevation(cellI) + delta / numCornersOnCell
                                    cells.setElevation(cellI, newElevationI)
                                    done[cellI]++
                                    anyCellChanged = true
                                } // else division by zero
                            }
                        }

                        if (anyCellChanged) {
                            // enqueue
                            queue.add(neighbor)
                        }
                    }
                }
            }
        }
    }

    fun landCorners(corners: CornerList): IntArrayList {
        val result = IntArrayList(corners.size)
        for (i in corners.indices) {
            if (!corners.isOcean(i) && !corners.isCoast(i)) {
                result.add(i)
            }
        }
        return result
    }

    fun assignCornerElevations(islandShape: IslandShape, corners: CornerList, size: Float) {
        val queue: ArrayDeque<Int> = ArrayDeque()

        // Initialize border corners
        val invSize = 1f / size
        for (c in corners.indices) {
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
            corners.neighbors.forEach(q) { s ->
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

    fun assignOceanCoastAndLand(cells: CellList, corners: CornerList) {
        // todo probably could use a more efficient data structure without encapsulation
        val queue = ArrayDeque<Int>()

        // Step 1: Mark border polygons as ocean
        for (ci in cells.indices) {
            var numWater = 0
            cells.corners.forEach(ci) { q ->
                if (corners.isBorder(q)) {
                    cells.setOcean(ci, true)
                    cells.setBorder(ci, true)
                    corners.setWater(q, true)
                    queue.addLast(ci)
                }
                if (corners.isWater(q)) numWater++
            }
            // Mark lakes
            cells.setWater(
                ci, cells.isOcean(ci) ||
                        numWater >= cells.corners.getSize(ci) * LAKE_THRESHOLD
            )
        }

        // Step 2: Flood-fill ocean from borders
        while (queue.isNotEmpty()) {
            val ci = queue.removeFirst()
            cells.neighbors.forEach(ci) { ni ->
                if (cells.isWater(ni) && !cells.isOcean(ni)) {
                    cells.setOcean(ni, true)
                    queue.addLast(ni)
                }
            }
        }

        // Step 3: Mark coasts for polygons
        for (ci in cells.indices) {
            var numOcean = 0
            var numLand = 0
            cells.neighbors.forEach(ci) { r ->
                if (cells.isOcean(r)) numOcean++
                if (!cells.isWater(r)) numLand++
            }
            cells.setCoast(ci, numOcean > 0 && numLand > 0)
        }

        // Step 4: Update corners
        for (q in corners.indices) {
            var numOcean = 0
            var numLand = 0
            val numTouches = corners.cells.forEach(q) { p ->
                if (cells.isOcean(p)) numOcean++
                if (!cells.isWater(p)) numLand++
            }
            corners.setOcean(q, numOcean == numTouches)
            corners.setCoast(q, numOcean > 0 && numLand > 0)
            corners.setWater(
                q, corners.isBorder(q) ||
                        ((numLand != numTouches) && !corners.isCoast(q))
            )
        }
    }

    fun assignPolygonElevations(cells: CellList, corners: CornerList) {
        for (p in cells.indices) {
            var sumElevation = 0f
            val size = cells.corners.forEach(p) { q ->
                sumElevation += corners.getElevation(q)
            }
            cells.setElevation(p, sumElevation / size)
        }
    }

    fun calculateDownslopes(corners: CornerList) {
        for (q in corners.indices) {
            var lowest = q
            corners.neighbors.forEach(q) { s ->
                if (corners.getElevation(s) <= corners.getElevation(lowest)) {
                    lowest = s
                }
            }
            corners.setDownslope(q, lowest)
        }
    }

    fun calculateWatersheds(corners: CornerList) {
        // Step 1: Initialize watershed pointers
        for (q in corners.indices) {
            val watershed = if (!corners.isOcean(q) && !corners.isCoast(q)) corners.getDownslope(q) else q
            corners.setWatershed(q, watershed)
            corners.setWatershedSize(q, 0)
        }

        // Step 2: Iteratively propagate watersheds
        repeat(100) {
            var changed = false
            for (q in corners.indices) {
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
        for (q in corners.indices) {
            val watershed = corners.getWatershed(q)
            if (watershed < 0) continue
            corners.setWatershedSize(watershed, corners.getWatershedSize(watershed) + 1)
        }
    }

    fun createRivers(corners: CornerList, edges: EdgeList, numPoints: Int, mapRandom: Random) {
        if (corners.size == 0) return

        val maxAttempts = numPoints / 30
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

                val edge = findEdgeFromCorners(corners, current, corners.getDownslope(current), edges)
                if (edge != -1) {
                    edges.setRiver(edge)
                    corners.incRiverFlowStrength(current)
                    corners.incRiverFlowStrength(corners.getDownslope(current))
                }
                current = corners.getDownslope(current)
            }
        }
    }

    fun assignCornerMoisture(corners: CornerList) {
        // todo use data-structure without boxing
        val queue = ArrayDeque<Int>()

        // Step 1: Initialize moisture and enqueue freshwater sources
        for (q in corners.indices) {
            if ((corners.isWater(q) || corners.getRiverFlowStrength(q) > 0) && !corners.isOcean(q)) {
                val moisture = if (corners.getRiverFlowStrength(q) > 0) {
                    min(3f, 0.2f * corners.getRiverFlowStrength(q))
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
            corners.neighbors.forEach(q) { r ->
                val newMoisture = corners.getMoisture(q) * 0.9f
                if (newMoisture > corners.getMoisture(r)) {
                    corners.setMoisture(r, newMoisture)
                    queue.addLast(r)
                }
            }
        }

        // Step 3: Saltwater corners get full moisture
        for (q in corners.indices) {
            if (corners.isOcean(q) || corners.isCoast(q)) {
                corners.setMoisture(q, 1f)
            }
        }
    }

    fun assignPolygonMoisture(cells: CellList, corners: CornerList) {
        for (ci in cells.indices) {
            var sumMoisture = 0f
            val size = cells.corners.forEach(ci) { q ->
                if (corners.getMoisture(q) > 1.0) corners.setMoisture(q, 1f)
                sumMoisture += corners.getMoisture(q)
            }
            cells.setMoisture(ci, sumMoisture / size)
        }
    }

    fun assignBiomes(cells: CellList) {
        for (p in cells.indices) {
            cells.setBiome(p, getBiome(cells, p))
        }
    }

    fun findEdgeFromCorners(corners: CornerList, cornerA: Int, cornerB: Int, edges: EdgeList): Int {
        corners.edges.forEach(cornerA) { edge ->
            val v0 = edges.getCornerA(edge)
            val v1 = edges.getCornerB(edge)
            if (v0 == cornerB || v1 == cornerB) return edge
        }
        return -1
    }

    fun inside(islandShape: IslandShape, invSize: Float, p: Vector2f): Boolean {
        val nx = p.x * invSize * 2f - 1f
        val ny = p.y * invSize * 2f - 1f
        return islandShape.isOnLand(nx, ny)
    }

    fun redistributeElevations(corners: CornerList, locations: IntArrayList) {
        val scaleFactorSq = 1.1f
        val scaleFactor = sqrt(scaleFactorSq)

        // Sort corners by elevation ascending
        val sorted = locations.sortedBy { corners.getElevation(it) }

        val invLast = 1f / max(sorted.lastIndex, 1)
        for (i in sorted.indices) {
            val y = i * invLast
            // Solve x^2 - 2x + y = 0 -> x = 1 - sqrt(1 - y)
            var x = scaleFactor * (1f - sqrt(1f - y))
            if (x > 1f) x = 1f
            corners.setElevation(sorted[i], x)
        }
    }

    fun redistributeMoisture(corners: CornerList, locations: IntArrayList) {
        // Sort corners by moisture ascending
        val sorted = locations.sortedBy { corners.getMoisture(it) }

        val invLast = 1f / max(sorted.lastIndex, 1)
        for (i in sorted.indices) {
            corners.setMoisture(sorted[i], i * invLast)
        }
    }

    fun hasRivers(map: GeneratedMap, corner: Int): Boolean {
        map.corners.edges.forEach(corner) { edge ->
            if (map.edges.hasRiver(edge)) return false
        }
        return true
    }

    fun flattenLines(map: GeneratedMap, allowChangingRivers: Boolean, filter: IntPredicate) {
        val roads = IntArrayList()
        val edges = map.edges
        for (edge in edges.indices) {
            val v0 = edges.getCornerA(edge)
            val v1 = edges.getCornerB(edge)
            if (v0 >= 0 && v1 >= 0 && filter.test(edge)) {
                if (!allowChangingRivers) {
                    // todo this seems incorrect... roads aren't flattened as much as I'd expect :/
                    if (hasRivers(map, v0) || hasRivers(map, v1)) {
                        continue
                    }
                }
                roads.add(edge)
            }
        }
        val numIterations = 10
        val corners = map.corners
        repeat(numIterations) {
            for (j in roads.indices) {
                val road = roads[j]
                val v0 = edges.getCornerA(road)
                val v1 = edges.getCornerB(road)
                val h0 = corners.getElevation(v0)
                val h1 = corners.getElevation(v1)
                changeCornerHeight(map, v0, mix(h0, h1, 0.25f))
                changeCornerHeight(map, v1, mix(h0, h1, 0.75f))
            }
        }
    }

    fun changeCornerHeight(map: GeneratedMap, corner: Int, newHeight: Float) {
        val cells = map.cells
        val corners = map.corners

        val delta = newHeight - corners.getElevation(corner)
        corners.setElevation(corner, newHeight)

        corners.cells.forEach(corner) { cell ->
            val biome = cells.getBiome(cell)
            when (biome) {
                Biome.LAKE, Biome.OCEAN, Biome.LAVA -> {}
                else -> {
                    val numCorners = cells.corners.getSize(cell)
                    if (numCorners > 0) {
                        val cellHeight = cells.getElevation(cell)
                        cells.setElevation(cell, cellHeight + delta / numCorners)
                    }
                }
            }
        }
    }

    private fun IntArrayList.sortedBy(map: (Int) -> Float): IntArrayList {
        val self = this
        object : Sortable {
            override fun move(dstI: Int, srcI: Int) {
                self[dstI] = self[srcI]
            }

            var tmp = 0
            override fun store(srcI: Int) {
                tmp = self[srcI]
            }

            override fun restore(dstI: Int) {
                self[dstI] = tmp
            }

            override fun compare(indexI: Int, indexJ: Int): Int {
                return map(self[indexI]).compareTo(map(self[indexJ]))
            }

            override fun swap(indexI: Int, indexJ: Int) {
                self.swap(indexI, indexJ)
            }
        }.sortWith2(0, size)
        return this
    }

}
