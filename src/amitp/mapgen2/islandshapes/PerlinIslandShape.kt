package amitp.mapgen2.islandshapes

import me.anno.maths.noise.PerlinNoise

class PerlinIslandShape(seed: Long) : IslandShape {

    val perlin = PerlinNoise(seed, 5, 0.5f, -1f, 2f)

    override fun isOnLand(x: Float, y: Float): Boolean {
        val noiseValue = perlin[x * 6f, y * 6f] // scale factor like 64 in AS
        return noiseValue > x * x + y * y
    }
}