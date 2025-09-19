package amitp.mapgen2.graphbuilder

import amitp.mapgen2.GeneratedMap
import org.joml.Vector2f

interface GraphBuilder {
    fun buildGraph(size: Vector2f, numCells: Int, seed: Long): GeneratedMap
}