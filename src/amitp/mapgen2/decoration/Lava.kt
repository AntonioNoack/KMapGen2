package amitp.mapgen2.decoration

import amitp.mapgen2.Biome
import amitp.mapgen2.GeneratedMap
import me.anno.utils.structures.arrays.IntArrayList
import kotlin.math.min
import kotlin.random.Random

object Lava {
    /**
     * Defines lava fields at the highest points, and some lava flows around that.
     * Ideally, lava should be set below the highest point in a pool, but our height gen doesn't support that.
     *
     * This method fixes the peak to local minimum.
     * */
    fun generateLava(
        map: GeneratedMap, random: Random,
        volcanoProbability: Float = 1f,
        flowProbability: Float = 0.97f
    ) {
        val cells = map.cells
        val corners = map.corners
        val edges = map.edges

        val nextLavaCorners = IntArrayList()

        searchForVolcanoPeaks@ for (c in 0 until cells.size) {
            if (cells.isWater(c) || cells.getMoisture(c) > 0.3f) continue
            val height = cells.getElevation(c)
            if (height < 0.8f) continue

            cells.edges.forEach(c) { e ->
                if (edges.hasRiver(e)) continue@searchForVolcanoPeaks
            }

            var newHeight = height
            cells.neighbors.forEach(c) { n ->
                val heightI = cells.getElevation(n)
                if (heightI > height) continue@searchForVolcanoPeaks

                newHeight = min(height, newHeight)
            }

            if (random.nextFloat() > volcanoProbability) continue

            cells.setBiome(c, Biome.LAVA)
            cells.corners.forEach(c) { corner ->
                nextLavaCorners.add(corner)
            }

            while (nextLavaCorners.isNotEmpty()) {
                val corner = nextLavaCorners.removeLast()
                var bestHeight = corners.getElevation(corner)
                var bestNextCorner = -1
                var bestNextEdge = -1
                corners.edges.forEach(corner) { e ->
                    if (!edges.hasLava(e) &&
                        !edges.hasRiver(e) &&
                        edges.getCellA(e) != c &&
                        edges.getCellB(e) != c
                    ) {
                        val c0 = edges.getCornerA(e)
                        val c1 = edges.getCornerB(e)
                        val other = if (c0 == corner) c1 else c0
                        if (!corners.isWater(other) &&
                            corners.getElevation(other) < bestHeight
                        ) {
                            bestNextEdge = e
                            bestNextCorner = other
                            bestHeight = corners.getElevation(other)
                        }
                    }
                }

                // make flow probability depend on moisture??
                if (bestNextCorner >= 0 && random.nextFloat() < flowProbability) {
                    edges.setLava(bestNextEdge)
                    nextLavaCorners.add(bestNextCorner)
                }
            }

            // extrapolate height
            // decrease pool height
            newHeight = newHeight * 2f - height
            cells.setElevation(c, newHeight)
            cells.corners.forEach(c) { corner ->
                corners.setElevation(corner, newHeight)
            }
        }
    }
}