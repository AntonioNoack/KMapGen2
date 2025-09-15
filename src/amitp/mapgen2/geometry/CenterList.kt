package amitp.mapgen2.geometry

import amitp.mapgen2.Biome
import amitp.mapgen2.utils.PackedIntLists

class CenterList(size: Int): PointList(size) {

    companion object {
         const val WATER_FLAG = 1
         const val OCEAN_FLAG = 2
         const val COAST_FLAG = 4
        const val BORDER_FLAG = 8
    }

    private val biomes = ByteArray(size)

    fun getBiome(index: Int): Biome = Biome.entries[biomes[index].toInt()]
    fun setBiome(index: Int, biome: Biome) {
        biomes[index] = biome.ordinal.toByte()
    }

    val neighbors = PackedIntLists(size, 8) // center indices
    val corners = PackedIntLists(size, 8)
    val borders = PackedIntLists(size, 8) // edge indices
}
