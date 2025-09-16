package amitp.mapgen2

import amitp.mapgen2.structures.CellList

enum class Biome {

    // first water types
    OCEAN,
    BEACH,
    MARSH,
    ICE,
    LAKE,

    SNOW,
    TUNDRA,
    BARE,
    SCORCHED,

    TAIGA,
    SHRUBLAND,
    TEMPERATE_DESERT,

    TEMPERATE_RAIN_FOREST,
    TEMPERATE_DECIDUOUS_FOREST,
    GRASSLAND,
    // temperate desert again

    TROPICAL_RAIN_FOREST,
    TROPICAL_SEASONAL_FOREST,
    // grassland again,
    SUBTROPICAL_DESERT,

    LAVA,

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