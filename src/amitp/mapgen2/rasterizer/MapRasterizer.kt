package amitp.mapgen2.rasterizer

import amitp.mapgen2.Biome
import amitp.mapgen2.GeneratedMap
import amitp.mapgen2.structures.GetFloat
import me.anno.image.raw.ByteImage
import me.anno.image.raw.ByteImageFormat
import me.anno.image.raw.FloatImage
import me.anno.maths.Maths.clamp
import me.anno.maths.Maths.posMod
import me.anno.maths.Maths.sq
import me.anno.maths.Packing.pack32
import me.anno.maths.Packing.unpackHighFrom32
import me.anno.maths.Packing.unpackLowFrom32
import me.anno.utils.hpc.threadLocal
import me.anno.utils.structures.arrays.IntArrayList
import me.anno.utils.types.Booleans.hasFlag
import me.anno.utils.types.Floats.toIntOr
import org.joml.AABBf
import org.joml.Vector2f
import org.joml.Vector3f
import speiger.primitivecollections.callbacks.IntPredicate
import kotlin.math.*

/**
 * Converts a given map into a raster image.
 * Available properties: biome, height.
 * */
object MapRasterizer {

    private const val MAX_VERTICES_PER_POLYGON = 16

    fun rasterizeBiomes(map: GeneratedMap, scale: Vector2f): ByteImage {
        val sizeX = (map.size.x * scale.x).toInt()
        val sizeY = (map.size.y * scale.y).toInt()
        val result = ByteImage(sizeX, sizeY, ByteImageFormat.R)

        val cells = map.cells
        val corners = map.corners

        val invScaleX = 1f / scale.x
        val invScaleY = 1f / scale.y
        val bounds = AABBf()
        val pt = Vector2f()
        val vertices = List(MAX_VERTICES_PER_POLYGON) { Vector3f() }
        for (i in cells.indices) {
            val biomeIndex = cells.getBiome(i).ordinal
            if (biomeIndex == 0) continue // fast-path

            bounds.clear()
            var numCorners = 0
            cells.corners.forEach(i) { corner ->
                val vertex = vertices[numCorners].set(
                    corners.getPointX(corner),
                    corners.getPointY(corner), 0f
                )
                bounds.union(vertex)
                numCorners++
            }

            val minX = floor(bounds.minX * scale.x).toInt()
            val minY = floor(bounds.minY * scale.y).toInt()
            val maxX = ceil(bounds.maxX * scale.x).toInt()
            val maxY = ceil(bounds.maxY * scale.y).toInt()

            for (y in minY..maxY) {
                for (x in minX..maxX) {
                    pt.set(x * invScaleX, y * invScaleY)
                    if (polygonXYContainsPoint(vertices, pt, numCorners)) {
                        result.data[result.getIndex(x, y)] = biomeIndex.toByte()
                    }
                }
            }
        }

        return result
    }

    // todo give some random curviness to all of these line drawing methods
    // todo river and lava could use some steepness dependency for straightness / extra turns
    // todo for water also render the river depth onto it...

    fun rasterizeRiversOntoBiomes(map: GeneratedMap, scale: Vector2f, riverThickness: Float, biomes: ByteImage) {
        rasterizeLinesOntoBiomes(map, scale, riverThickness, biomes, {
            when (it) {
                Biome.LAVA, Biome.OBSIDIAN -> Biome.OBSIDIAN
                else -> Biome.RIVER
            }
        }) { edge ->
            map.edges.hasRiver(edge)
        }
    }

    fun rasterizeLavaOntoBiomes(map: GeneratedMap, scale: Vector2f, lavaThickness: Float, biomes: ByteImage) {
        rasterizeLinesOntoBiomes(map, scale, lavaThickness, biomes, {
            when (it) {
                Biome.OCEAN, Biome.LAKE, Biome.RIVER, Biome.OBSIDIAN -> Biome.OBSIDIAN
                else -> Biome.LAVA
            }
        }) { edge ->
            map.edges.hasLava(edge)
        }
    }

    fun rasterizeRoadsOntoBiomes(map: GeneratedMap, scale: Vector2f, roadThickness: Float, biomes: ByteImage) {
        rasterizeLinesOntoBiomes(map, scale, roadThickness, biomes, {
            when (it) {
                Biome.OCEAN, Biome.LAKE, Biome.RIVER, Biome.RIVER_BRIDGE -> Biome.RIVER_BRIDGE
                Biome.LAVA, Biome.LAVA_BRIDGE -> Biome.LAVA_BRIDGE
                else -> Biome.ROAD
            }
        }) { edge ->
            map.edges.isRoad(edge)
        }
    }

    /**
     * find all shore pixels (land vs ocean|lake|river), write depth=1,
     *    then process their neighbors and so forth
     * */
    fun rasterizeShoreDistance(biomes: ByteImage): ByteImage {

        val width = biomes.width
        val height = biomes.height
        val distance = ByteImage(width, height, ByteImageFormat.R)
        val distances = distance.data
        val queue = IntArrayList()

        val ocean = Biome.OCEAN.ordinal.toByte()
        val lake = Biome.LAKE.ordinal.toByte()
        val river = Biome.RIVER.ordinal.toByte()
        val bridge = Biome.RIVER_BRIDGE.ordinal.toByte()

        val biomeData = biomes.data
        fun isWater(x: Int, y: Int): Boolean {
            val biome = biomeData[biomes.getIndex(x, y)]
            return when (biome) {
                ocean, lake, river, bridge -> true
                else -> false
            }
        }

        for (x in 0 until width) {
            for (y in 0 until height) {
                if (!isWater(x, y)) continue
                when {
                    (x > 0 && !isWater(x - 1, y)) ||
                            (y > 0 && !isWater(x, y - 1)) ||
                            (x + 1 < width && !isWater(x + 1, y)) ||
                            (y + 1 > height && !isWater(x, y + 1)) -> {
                        // is on shore -> mark pixel as 1 deep
                        queue.add(pack32(x, y))
                        distances[x + y * width] = 1
                    }
                }
            }
        }

        var depth = 1.toByte()

        fun check(x: Int, y: Int, index: Int) {
            if (x in 0 until width &&
                y in 0 until height &&
                distances[index] == 0.toByte() &&
                isWater(x, y)
            ) {
                distances[index] = depth
                queue.add(pack32(x, y))
            }
        }

        while (depth != 0.toByte() && queue.isNotEmpty()) {
            val startIndex = queue.size
            for (i in queue.indices) {
                val value = queue[i]
                val x = unpackHighFrom32(value, false)
                val y = unpackLowFrom32(value, false)

                // check all four corners for water & depth == 0
                val index = x + y * width
                check(x - 1, y, index - 1)
                check(x + 1, y, index + 1)
                check(x, y - 1, index - width)
                check(x, y + 1, index + width)
            }

            queue.removeBetween(0, startIndex)
            depth++
        }

        // fill all water pixels with depth=0 to depth=255
        for (x in 0 until width) {
            for (y in 0 until height) {
                if (!isWater(x, y)) continue

                val index = x + y * width
                if (distance.data[index] == 0.toByte()) {
                    distance.data[index] = 255.toByte()
                }
            }
        }

        return distance
    }

    fun rasterizeLinesOntoBiomes(
        map: GeneratedMap, scale: Vector2f, lineThickness: Float,
        biomes: ByteImage, writtenBiome: (Biome) -> Biome, filter: IntPredicate,
    ) {
        val corners = map.corners
        val edges = map.edges
        for (edge in edges.indices) {
            val v0 = edges.getCornerA(edge)
            val v1 = edges.getCornerB(edge)
            if (v0 < 0 || v1 < 0 || !filter.test(edge)) continue

            val x0 = corners.getPointX(v0) * scale.x
            val y0 = corners.getPointY(v0) * scale.y
            val x1 = corners.getPointX(v1) * scale.x
            val y1 = corners.getPointY(v1) * scale.y

            // could be expensive for diagonal lines...
            val minX = max(floor(min(x0, x1) - lineThickness).toIntOr(), 0)
            val minY = max(floor(min(y0, y1) - lineThickness).toIntOr(), 0)
            val maxX = min(floor(max(x0, x1) + lineThickness).toIntOr(), biomes.width - 1)
            val maxY = min(floor(max(y0, y1) + lineThickness).toIntOr(), biomes.height - 1)

            val dx = x1 - x0
            val dy = y1 - y0
            val invDLenSq = 1f / sq(dx, dy)
            val dotA = x0 * dx + y0 * dy

            val radiusSq = sq(lineThickness)
            val biomeData = biomes.data
            for (yi in minY..maxY) {
                for (xi in minX..maxX) {
                    val xf = xi.toFloat()
                    val yf = yi.toFloat()

                    val dotPt = xf * dx + yf * dy
                    val t = clamp((dotPt - dotA) * invDLenSq)
                    val closestX = x0 + dx * t
                    val closestY = y0 + dy * t
                    if (sq(closestX - xf, closestY - yf) < radiusSq) {
                        val biomeIndex = biomes.getIndex(xi, yi)
                        val oldBiome = Biome.entries[biomeData[biomeIndex].toInt()]
                        biomeData[biomeIndex] = writtenBiome(oldBiome).ordinal.toByte()
                    }
                }
            }
        }
    }

    fun rasterizeHeight(map: GeneratedMap, scale: Vector2f): FloatImage {
        val corners = map.corners
        return rasterizeCornerFloat(map, scale, 0f) { corner -> corners.getElevation(corner) }
    }

    fun rasterizeMoisture(map: GeneratedMap, scale: Vector2f): FloatImage {
        val corners = map.corners
        return rasterizeCornerFloat(map, scale, 1f) { corner -> corners.getMoisture(corner) }
    }

    fun rasterizeCornerFloat(map: GeneratedMap, scale: Vector2f, default: Float, getFloat: GetFloat): FloatImage {
        val sizeX = (map.size.x * scale.x).toInt()
        val sizeY = (map.size.y * scale.y).toInt()
        val result = FloatImage(sizeX, sizeY, 1)
        if (default != 0f) result.data.fill(default)

        val cells = map.cells
        val corners = map.corners

        val invScaleX = 1f / scale.x
        val invScaleY = 1f / scale.y
        val bounds = AABBf()
        val pt = Vector2f()
        val vertices = List(MAX_VERTICES_PER_POLYGON) { Vector3f() }
        for (i in cells.indices) {
            val biomeIndex = cells.getBiome(i).ordinal
            if (biomeIndex == 0) continue // fast-path

            bounds.clear()
            var numCorners = 0
            cells.corners.forEach(i) { corner ->
                val vertex = vertices[numCorners].set(
                    corners.getPointX(corner),
                    corners.getPointY(corner),
                    getFloat.map(corner)
                )
                bounds.union(vertex)
                numCorners++
            }

            val minX = floor(bounds.minX * scale.x).toInt()
            val minY = floor(bounds.minY * scale.y).toInt()
            val maxX = floor(bounds.maxX * scale.x).toInt()
            val maxY = floor(bounds.maxY * scale.y).toInt()

            for (y in minY..maxY) {
                for (x in minX..maxX) {
                    pt.set(x * invScaleX, y * invScaleY)
                    if (polygonXYContainsPoint(vertices, pt, numCorners)) {
                        result.data[result.getIndex(x, y)] = interpolateZByXYMeanValue(vertices, pt, numCorners)
                    }
                }
            }
        }

        return result
    }

    fun polygonXYContainsPoint(vertices: List<Vector3f>, point: Vector2f, numVertices: Int = vertices.size): Boolean {
        val px = point.x
        val py = point.y

        var contained = 0
        var i = 0
        var j = numVertices - 1
        while (i < numVertices) {
            val vi = vertices[i]
            val vj = vertices[j]
            if (((vi.y > py) != (vj.y > py)) &&
                (px < (vj.x - vi.x) * (py - vi.y) / (vj.y - vi.y) + vi.x)
            ) {
                contained++
            }
            j = i++
        }

        return contained.hasFlag(1)
    }

    /**
     * Simple interpolation of a value (z component) in a polygon.
     * Curves values on the middles of edges towards the average.
     * */
    fun interpolateZByXYSimple(vertices: List<Vector3f>, point: Vector2f, numVertices: Int = vertices.size): Float {

        var height = 0f
        var sum = 0f

        for (i in 0 until numVertices) {
            val vertex = vertices[i]
            val weight = 1f / point.distanceSquared(vertex.x, vertex.y)
            height += vertex.z * weight
            sum += weight
        }

        return height / sum
    }

    private val angles = threadLocal { FloatArray(MAX_VERTICES_PER_POLYGON) }

    /**
     * More complex interpolation of a value (z component) in a polygon, called "mean-value".
     * The edges are stable and have a linear transition.
     * */
    fun interpolateZByXYMeanValue(vertices: List<Vector3f>, point: Vector2f, numVertices: Int = vertices.size): Float {

        val theta = angles.get()
        for (i in 0 until numVertices) {
            val v0 = vertices[i]
            val v1 = vertices[posMod(i + 1, numVertices)]

            val v1x = v0.x - point.x
            val v1y = v0.y - point.y

            val v2x = v1.x - point.x
            val v2y = v1.y - point.y

            val cross = v1x * v2y - v1y * v2x
            val dot = v1x * v2x + v1y * v2y
            theta[i] = tan(atan2(cross, dot) * 0.5f)
        }

        var sumValue = 0f
        var sumWeight = 0f

        var lastTheta = theta[numVertices - 1]
        for (i in 0 until numVertices) {
            val vertex = vertices[i]

            val currTheta = theta[i]
            val weight = (lastTheta + currTheta) / max(point.distance(vertex.x, vertex.y), 1e-30f)
            lastTheta = currTheta

            sumValue += vertex.z * weight
            sumWeight += weight
        }

        return sumValue / sumWeight
    }

}