package amitp.mapgen2

import amitp.mapgen2.structures.CellList

enum class Biome(val debugColor: Int) {

    // first water types
    OCEAN(0x006994),
    BEACH(0xeed6af),
    MARSH(0x2f4f4f),
    ICE(0xb4dcff),
    LAKE(0x1c6ba0),

    SNOW(0xfffafa),
    TUNDRA(0xb0c4de),
    BARE(0x8b8989),
    SCORCHED(0x505050),

    TAIGA(0x006400),
    SHRUBLAND(0xa0522d),
    TEMPERATE_DESERT(0xd2b48c),

    TEMPERATE_RAIN_FOREST(0x008000),
    TEMPERATE_DECIDUOUS_FOREST(0x228b22),
    GRASSLAND(0x7cfc00),
    // temperate desert again

    TROPICAL_RAIN_FOREST(0x006400),
    TROPICAL_SEASONAL_FOREST(0x228b22),

    // grassland again,
    SUBTROPICAL_DESERT(0xedc9af),

    // lava-lake biome, occurs on the inside of volcanoes
    LAVA(0xff9600),

    // pseudo-biomes
    ROAD(0x555555),
    RIVER(0x3399ff),

    // placed when roads overlaps water/lava
    RIVER_BRIDGE(0x6888a3),
    LAVA_BRIDGE(0xa37468),

    // placed when lava overlaps water
    OBSIDIAN(0x58249c),

    ;

    companion object {
        fun getBiome(p: CellList, i: Int): Biome {
            val elevation = p.getElevation(i)
            val moisture = p.getMoisture(i)
            return when {
                p.isOcean(i) -> OCEAN
                p.isWater(i) -> when {
                    elevation < 0.1 -> MARSH
                    elevation > 0.8 -> ICE
                    else -> LAKE
                }
                p.isCoast(i) -> BEACH
                elevation > 0.8 -> when {
                    moisture > 0.50 -> SNOW
                    moisture > 0.33 -> TUNDRA
                    moisture > 0.16 -> BARE
                    else -> SCORCHED
                }
                elevation > 0.6 -> when {
                    moisture > 0.66 -> TAIGA
                    moisture > 0.33 -> SHRUBLAND
                    else -> TEMPERATE_DESERT
                }
                elevation > 0.3 -> when {
                    moisture > 0.83 -> TEMPERATE_RAIN_FOREST
                    moisture > 0.50 -> TEMPERATE_DECIDUOUS_FOREST
                    moisture > 0.16 -> GRASSLAND
                    else -> TEMPERATE_DESERT
                }
                else -> when {
                    moisture > 0.66 -> TROPICAL_RAIN_FOREST
                    moisture > 0.33 -> TROPICAL_SEASONAL_FOREST
                    moisture > 0.16 -> GRASSLAND
                    else -> SUBTROPICAL_DESERT
                }
            }
        }
    }
}