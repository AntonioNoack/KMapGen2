package amitp.mapgen2.structures

import amitp.mapgen2.Biome

class CenterList(size: Int) : PointList(size) {

    private val biomes = ByteArray(size)

    fun getBiome(index: Int): Biome = Biome.entries[biomes[index].toInt()]
    fun setBiome(index: Int, biome: Biome) {
        biomes[index] = biome.ordinal.toByte()
    }

    /**
     * neighbor center indices for each center
     *
     * todo order them
     * */
    val neighbors = PackedIntLists(size, 8)

    /**
     * corners for each center; not ordered
     *
     * todo order them, because who wouldn't want them ordered?
     * */
    val corners = PackedIntLists(size, 8)

    /**
     * edge indices for each center
     *
     * todo order them
     * */
    val edges = PackedIntLists(size, 8)
}
