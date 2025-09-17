package amitp.mapgen2.graphbuilder

import amitp.mapgen2.GeneratedMap

interface GraphBuilder {
    fun buildGraph(size: Float, numCells: Int, seed: Long): GeneratedMap
}