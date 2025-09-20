package amitp.mapgen2.pointselector

import org.joml.Vector2f
import kotlin.math.sqrt
import kotlin.random.Random

class GridPointSelector(val randomness: Float) : PointSelector {
    override fun select(size: Vector2f, numCells: Int, seed: Long): FloatArray {
        val aspect = size.x / size.y
        val numCellsX = sqrt(numCells * aspect).toInt()
        val numCellsY = sqrt(numCells / aspect).toInt()
        val points = FloatArray(numCellsX * numCellsY * 2)
        val rnd = Random(seed)
        val factorX = size.x / numCellsX
        val factorY = size.y / numCellsY
        val offset = (1f - randomness) * 0.5f // center the points
        var k = 0
        for (yi in 0 until numCellsY) {
            for (xi in 0 until numCellsX) {
                points[k++] = (xi + offset + randomness * rnd.nextFloat()) * factorX
                points[k++] = (yi + offset + randomness * rnd.nextFloat()) * factorY
            }
        }
        return points
    }
}