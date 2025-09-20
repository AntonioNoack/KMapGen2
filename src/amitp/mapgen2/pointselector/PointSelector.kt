package amitp.mapgen2.pointselector

import org.joml.Vector2f

interface PointSelector {
    fun select(size: Vector2f, numCells: Int, seed: Long): FloatArray
}