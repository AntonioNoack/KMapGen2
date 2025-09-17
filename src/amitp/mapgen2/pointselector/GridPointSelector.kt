package amitp.mapgen2.pointselector

import kotlin.math.sqrt
import kotlin.random.Random

class GridPointSelector(val randomness: Float) : PointSelector {
    override fun select(size: Float, numPoints: Int, seed: Long): FloatArray {
        val sqrtN = sqrt(numPoints.toFloat()).toInt()
        val points = FloatArray(sqrtN * sqrtN * 2)
        val rnd = Random(seed)
        val factor = size / sqrtN
        val offset = (1f - randomness) * 0.5f
        var k = 0
        for (i in 0 until sqrtN) {
            for (j in 0 until sqrtN) {
                points[k++] = (i + offset + randomness * rnd.nextFloat()) * factor
                points[k++] = (j + offset + randomness * rnd.nextFloat()) * factor
            }
        }
        return points
    }
}