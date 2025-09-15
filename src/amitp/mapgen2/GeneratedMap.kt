package amitp.mapgen2

import amitp.mapgen2.structures.CenterList
import amitp.mapgen2.structures.CornerList
import amitp.mapgen2.structures.EdgeList

/**
 * This is the result of the algorithm:
 *  cell centers, cell corners, and cell edges,
 *  within these structures, you can find their links from one to another, and to neighbor cells, too
 * */
class GeneratedMap(
    val centers: CenterList,
    val corners: CornerList,
    val edges: EdgeList,
)