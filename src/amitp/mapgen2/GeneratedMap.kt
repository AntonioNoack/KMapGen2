package amitp.mapgen2

import amitp.mapgen2.structures.CellList
import amitp.mapgen2.structures.CornerList
import amitp.mapgen2.structures.EdgeList

/**
 * This is the result of the algorithm:
 *  cell centers, corners, and edges,
 *  within these structures, you can find their links from one to another, and to neighbor cells, too
 * */
class GeneratedMap(
    val cells: CellList,
    val corners: CornerList,
    val edges: EdgeList,
    val size: Float
)