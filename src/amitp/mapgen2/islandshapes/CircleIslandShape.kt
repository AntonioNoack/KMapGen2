package amitp.mapgen2.islandshapes

object CircleIslandShape : IslandShape {
    override fun isOnLand(x: Float, y: Float): Boolean {
        return x * x + y * y < 0.25f
    }
}