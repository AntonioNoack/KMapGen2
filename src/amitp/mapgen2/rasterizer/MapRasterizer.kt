package amitp.mapgen2.rasterizer

import amitp.mapgen2.GeneratedMap
import me.anno.image.raw.ByteImage
import me.anno.image.raw.ByteImageFormat
import me.anno.image.raw.FloatImage
import me.anno.utils.types.Booleans.hasFlag
import org.joml.AABBf
import org.joml.Vector2f
import org.joml.Vector3f
import kotlin.math.ceil
import kotlin.math.floor

object MapRasterizer {

    fun rasterizeBiomes(map: GeneratedMap, scale: Float): ByteImage {
        val size = (map.size * scale).toInt()
        val result = ByteImage(size, size, ByteImageFormat.R)

        val cells = map.cells
        val corners = map.corners

        val invScale = 1f / scale
        val bounds = AABBf()
        val pt = Vector2f()
        val vertices = List(16) { Vector3f() }
        for (i in 0 until cells.size) {
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

            val minX = floor(bounds.minX * scale).toInt()
            val minY = floor(bounds.minY * scale).toInt()
            val maxX = ceil(bounds.maxX * scale).toInt()
            val maxY = ceil(bounds.maxY * scale).toInt()

            for (y in minY..maxY) {
                for (x in minX..maxX) {
                    pt.set(x * invScale, y * invScale)
                    if (polygonXYContainsPoint(vertices, pt, numCorners)) {
                        result.data[result.getIndex(x, y)] = biomeIndex.toByte()
                    }
                }
            }
        }

        return result
    }

    fun rasterizeHeight(map: GeneratedMap, scale: Float): FloatImage {
        val size = (map.size * scale).toInt()
        val result = FloatImage(size, size, 1)

        val cells = map.cells
        val corners = map.corners

        val invScale = 1f / scale
        val bounds = AABBf()
        val pt = Vector2f()
        val vertices = List(16) { Vector3f() }
        for (i in 0 until cells.size) {
            val biomeIndex = cells.getBiome(i).ordinal
            if (biomeIndex == 0) continue // fast-path

            bounds.clear()
            var numCorners = 0
            cells.corners.forEach(i) { corner ->
                val vertex = vertices[numCorners].set(
                    corners.getPointX(corner),
                    corners.getPointY(corner),
                    corners.getElevation(corner)
                )
                bounds.union(vertex)
                numCorners++
            }

            val minX = floor(bounds.minX * scale).toInt()
            val minY = floor(bounds.minY * scale).toInt()
            val maxX = floor(bounds.maxX * scale).toInt()
            val maxY = floor(bounds.maxY * scale).toInt()

            for (y in minY..maxY) {
                for (x in minX..maxX) {
                    pt.set(x * invScale, y * invScale)
                    if (polygonXYContainsPoint(vertices, pt, numCorners)) {
                        result.data[result.getIndex(x, y)] = interpolateZByXY(vertices, pt, numCorners)
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

    fun interpolateZByXY(vertices: List<Vector3f>, point: Vector2f, numVertices: Int = vertices.size): Float {

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

}