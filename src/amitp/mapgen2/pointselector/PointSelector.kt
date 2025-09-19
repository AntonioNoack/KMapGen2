package amitp.mapgen2.pointselector

import org.joml.Vector2f

interface PointSelector {
    fun select(size: Vector2f, numPoints: Int, seed: Long): FloatArray
}