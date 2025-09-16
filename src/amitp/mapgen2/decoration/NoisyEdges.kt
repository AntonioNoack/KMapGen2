package amitp.mapgen2.decoration

import amitp.mapgen2.GeneratedMap
import amitp.mapgen2.structures.PointList
import me.anno.maths.Maths.sq
import me.anno.utils.pooling.JomlPools
import me.anno.utils.structures.arrays.FloatArrayList
import me.anno.utils.structures.arrays.FloatArrayListUtils.add
import org.joml.Vector2f
import kotlin.random.Random

/**
 * Build noisy line paths for each of the Voronoi edges.
 * There is one noisy line for each edge, from offsets[edge+0] to offsets[edge+1].
 * */
class NoisyEdges(val offsets: IntArray, val points: FloatArray) {

    companion object {

        /**
         * Creates NoiseEdges instance.
         * @param jaggedness keep this below 0.5
         * @param invScaleFactor ~ one subdivision every X pixels
         * */
        fun generateNoisyEdges(
            map: GeneratedMap, random: Random,
            jaggedness: Float = 0.3f,
            invScaleFactor: Float = 10f
        ): NoisyEdges {
            val edges = map.edges
            val corners = map.corners
            val cells = map.cells
            val offsets = IntArray(edges.size + 1)
            val points = FloatArrayList(edges.size * 8)
            val pool = JomlPools.vec2f
            val index = pool.index

            for (edge in 0 until edges.size) {

                pool.index = index

                val v0 = edges.getCornerA(edge)
                val v1 = edges.getCornerB(edge)
                val d0 = edges.getCellA(edge)
                val d1 = edges.getCellB(edge)
                if (!(v0 < 0 || v1 < 0 || d0 < 0 || d1 < 0)) {
                    var minLength = 10f
                    if (cells.getBiome(d0) != cells.getBiome(d1)) minLength = 3f
                    if (cells.isOcean(d0) && cells.isOcean(d1)) minLength = 100f
                    if (cells.isCoast(d0) || cells.isCoast(d1)) minLength = 1f
                    if (edges.hasRiver(edge) || edges.hasLava(edge)) minLength = 1f
                    val minLengthSq = sq(minLength * invScaleFactor)

                    val a0 = get(corners, v0)
                    val a1 = get(corners, v1)

                    points.add(a0)
                    buildNoisyLineSegments(a0, a1, random, jaggedness, minLengthSq, points)
                    points.add(a1)
                }

                offsets[edge + 1] = points.size
            }

            pool.index = index
            return NoisyEdges(offsets, points.values)
        }

        /**
         * Helper function: build a single noisy line a0-a1,
         * and store the output points in points.
         * */
        private fun buildNoisyLineSegments(
            a0: Vector2f, a1: Vector2f,
            random: Random, jaggedness: Float, minLengthSq: Float,
            points: FloatArrayList,
        ) {

            val dx = a1.x - a0.x
            val dy = a1.y - a0.y
            val dist0 = dx * dx + dy * dy
            if (dist0 <= minLengthSq * jaggedness) return

            val extends = jaggedness * (if (random.nextBoolean()) +1f else -1f)

            val m = interpolate(a0, a1, 0.25f + random.nextFloat() * 0.5f)
                .add(dy * extends, -dx * extends)

            val newJaggedness = jaggedness * 0.9f
            buildNoisyLineSegments(a0, m, random, newJaggedness, minLengthSq, points)
            points.add(m)
            buildNoisyLineSegments(m, a1, random, newJaggedness, minLengthSq, points)
        }

        private fun interpolate(a: Vector2f, b: Vector2f, f: Float): Vector2f {
            return a.mix(b, f, JomlPools.vec2f.create())
        }

        private fun get(list: PointList, i: Int): Vector2f {
            return JomlPools.vec2f.create()
                .set(list.getPointX(i), list.getPointY(i))
        }
    }
}