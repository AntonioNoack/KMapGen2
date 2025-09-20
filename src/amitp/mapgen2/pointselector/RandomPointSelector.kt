package amitp.mapgen2.pointselector

import me.anno.utils.algorithms.ForLoop.forLoopSafely
import org.joml.Vector2f
import kotlin.random.Random

object RandomPointSelector : PointSelector {
    override fun select(size: Vector2f, numCells: Int, seed: Long): FloatArray {
        val rng = Random(seed)
        val dst = FloatArray(numCells * 2)
        forLoopSafely(dst.size, 2) { i ->
            dst[i] = rng.nextFloat() * size.x
            dst[i + 1] = rng.nextFloat() * size.y
        }
        return dst
    }
}