package amitp.mapgen2.structures

import amitp.mapgen2.Biome

class CellList(size: Int) : PointList(size) {

    private val biomes = ByteArray(size)

    fun getBiome(index: Int): Biome = Biome.entries[biomes[index].toInt()]
    fun setBiome(index: Int, biome: Biome) {
        biomes[index] = biome.ordinal.toByte()
    }

    override fun resizeTo(newSize: Int) {
        super.resizeTo(newSize)
        neighbors.resizeTo(newSize)
        corners.resizeTo(newSize)
        edges.resizeTo(newSize)
    }

    /**
     * neighbor cell indices for each cell, 6 on average
     * */
    val neighbors = PackedIntLists(size, 8)

    /**
     * corners for each cell, 6 on average
     * */
    val corners = PackedIntLists(size, 8)

    /**
     * edge indices for each cell, 6 on average
     * */
    val edges = PackedIntLists(size, 8)
}
