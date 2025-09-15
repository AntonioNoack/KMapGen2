package amitp.mapgen2.geometry

import amitp.mapgen2.Biome
import amitp.mapgen2.utils.PackedIntLists

class CenterList(size: Int) : PointList(size) {

    private val biomes = ByteArray(size)

    fun getBiome(index: Int): Biome = Biome.entries[biomes[index].toInt()]
    fun setBiome(index: Int, biome: Biome) {
        biomes[index] = biome.ordinal.toByte()
    }

    val neighbors = PackedIntLists(size, 8) // center indices
    val corners = PackedIntLists(size, 8)
    val borders = PackedIntLists(size, 8) // edge indices
}
