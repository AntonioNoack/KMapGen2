package amitp.mapgen2.pointselector

import org.joml.Vector2f
import kotlin.math.sqrt
import kotlin.random.Random

class GridPointSelector(val randomness: Float) : PointSelector {
    override fun select(size: Vector2f, numPoints: Int, seed: Long): FloatArray {
        val aspect = size.x / size.y
        val numPointsX = sqrt(numPoints * aspect).toInt()
        val numPointsY = sqrt(numPoints / aspect).toInt()
        val points = FloatArray(numPointsX * numPointsY * 2)
        val rnd = Random(seed)
        val factorX = size.x / numPointsX
        val factorY = size.y / numPointsY
        val offset = (1f - randomness) * 0.5f // center the points
        var k = 0
        for (yi in 0 until numPointsY) {
            for (xi in 0 until numPointsX) {
                points[k++] = (xi + offset + randomness * rnd.nextFloat()) * factorX
                points[k++] = (yi + offset + randomness * rnd.nextFloat()) * factorY
            }
        }
        return points
    }
}