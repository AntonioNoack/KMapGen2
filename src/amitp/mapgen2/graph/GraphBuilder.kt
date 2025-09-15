package amitp.mapgen2.graph

interface GraphBuilder {
    fun buildGraph(size: Float, numCells: Int, seed: Long): Graph
}