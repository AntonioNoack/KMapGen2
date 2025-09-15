package amitp.mapgen2.islandshapes

interface IslandShape {
    fun isOnLand(x: Float, y: Float): Boolean
}