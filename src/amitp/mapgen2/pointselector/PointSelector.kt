package amitp.mapgen2.pointselector

interface PointSelector {
    fun select(size: Float, numPoints: Int, seed: Long): FloatArray
}