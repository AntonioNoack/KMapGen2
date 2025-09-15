package amitp.mapgen2

import amitp.mapgen2.geometry.Center

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
    SUBTROPICAL_DESERT

    ;

    companion object {
        fun getBiome(p: Center): Biome {
            return when {
                p.ocean -> OCEAN
                p.water -> when {
                    p.elevation < 0.1 -> MARSH
                    p.elevation > 0.8 -> ICE
                    else -> LAKE
                }
                p.coast -> BEACH
                p.elevation > 0.8 -> when {
                    p.moisture > 0.50 -> SNOW
                    p.moisture > 0.33 -> TUNDRA
                    p.moisture > 0.16 -> BARE
                    else -> SCORCHED
                }
                p.elevation > 0.6 -> when {
                    p.moisture > 0.66 -> TAIGA
                    p.moisture > 0.33 -> SHRUBLAND
                    else -> TEMPERATE_DESERT
                }
                p.elevation > 0.3 -> when {
                    p.moisture > 0.83 -> TEMPERATE_RAIN_FOREST
                    p.moisture > 0.50 -> TEMPERATE_DECIDUOUS_FOREST
                    p.moisture > 0.16 -> GRASSLAND
                    else -> TEMPERATE_DESERT
                }
                else -> when {
                    p.moisture > 0.66 -> TROPICAL_RAIN_FOREST
                    p.moisture > 0.33 -> TROPICAL_SEASONAL_FOREST
                    p.moisture > 0.16 -> GRASSLAND
                    else -> SUBTROPICAL_DESERT
                }
            }
        }
    }
}