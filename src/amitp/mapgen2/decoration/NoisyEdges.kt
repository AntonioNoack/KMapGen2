package amitp.mapgen2.decoration

import amitp.mapgen2.GeneratedMap
import amitp.mapgen2.structures.PointList
import me.anno.maths.Maths.sq
import me.anno.utils.structures.arrays.FloatArrayList
import me.anno.utils.structures.arrays.FloatArrayListUtils.add
import org.joml.Vector2f
import kotlin.random.Random

/**
 * Build noisy line paths for each of the Voronoi edges.
 * There are two noisy line paths for each edge, each covering half the distance:
 * path0 is from v0 to the midpoint and path1 is from v1 to the midpoint.
 * When drawing the polygons, one or the other must be drawn in reverse order.
 * */
class NoisyEdges(val offsets: IntArray, val points: FloatArray) {

    companion object {

        // todo use pool instead of constantly allocating new vectors
        fun generateNoisyEdges(
            map: GeneratedMap, random: Random,
            jaggedness: Float = 1f
        ): NoisyEdges {
            val edges = map.edges
            val corners = map.corners
            val cells = map.cells
            val offsets = IntArray(edges.size + 1)
            val points = FloatArrayList(edges.size * 8)
            for (edge in 0 until edges.size) {
                val v0 = edges.getCornerA(edge)
                val v1 = edges.getCornerB(edge)
                val d0 = edges.getCellA(edge)
                val d1 = edges.getCellB(edge)
                if (v0 < 0 || v1 < 0 || d0 < 0 || d1 < 0) continue

                var minLength = 10f
                if (cells.getBiome(d0) != cells.getBiome(d1)) minLength = 3f
                if (cells.isOcean(d0) && cells.isOcean(d1)) minLength = 100f
                if (cells.isCoast(d0) || cells.isCoast(d1)) minLength = 1f
                if (edges.hasRiver(edge) || edges.hasLava(edge)) minLength = 1f
                val minLengthSq = sq(minLength)

                val a0 = get(corners, v0)
                val a1 = get(corners, v1)

                points.add(a0)
                buildNoisyLineSegments(a0, a1, random, jaggedness, minLengthSq, points)
                points.add(a1)

                offsets[edge + 1] = points.size
            }
            return NoisyEdges(offsets, points.values)
        }

        // Helper function: build a single noisy line in a quadrilateral A-B-C-D,
        // and store the output points in a Vector.
        fun buildNoisyLineSegments(
            a0: Vector2f, a1: Vector2f,
            random: Random, jaggedness: Float, minLengthSq: Float,
            points: FloatArrayList,
        ) {

            val dx = a1.x - a0.x
            val dy = a1.y - a0.y
            val dist0 = dx * dx + dy * dy
            if (dist0 <= minLengthSq || jaggedness < 0.1f) return

            val extends = jaggedness / dist0 * (if (random.nextBoolean()) +1f else -1f)

            val m = interpolate(a0, a1, 0.25f + random.nextFloat() * 0.5f)
                .add(dy * extends, -dx * extends)

            val newJaggedness = jaggedness * 0.8f
            buildNoisyLineSegments(a0, m, random, newJaggedness, minLengthSq, points)
            points.add(m)
            buildNoisyLineSegments(m, a1, random, newJaggedness, minLengthSq, points)
        }

        fun interpolate(a: Vector2f, b: Vector2f, f: Float): Vector2f {
            return a.mix(b, f, Vector2f())
        }

        fun get(list: PointList, i: Int): Vector2f {
            return Vector2f(list.getPointX(i), list.getPointY(i))
        }
    }
}