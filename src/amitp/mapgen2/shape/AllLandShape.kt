package amitp.mapgen2.shape

object AllLandShape : IslandShape {
    override fun isOnLand(x: Float, y: Float): Boolean = true
}