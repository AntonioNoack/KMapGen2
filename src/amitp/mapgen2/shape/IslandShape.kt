package amitp.mapgen2.shape

interface IslandShape {
    fun isOnLand(x: Float, y: Float): Boolean
}