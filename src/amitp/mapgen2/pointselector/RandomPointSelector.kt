package amitp.mapgen2.pointselector

import kotlin.random.Random

object RandomPointSelector : PointSelector {
    override fun select(size: Float, numPoints: Int, seed: Long): FloatArray {
        val rng = Random(seed)
        val dst = FloatArray(numPoints)
        for (i in dst.indices) {
            dst[i] = rng.nextFloat() * size
        }
        return dst
    }
}