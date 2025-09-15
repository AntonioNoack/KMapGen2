package amitp.mapgen2.graph

import amitp.mapgen2.geometry.CenterList
import amitp.mapgen2.geometry.Corner
import amitp.mapgen2.geometry.CornerList
import amitp.mapgen2.geometry.EdgeList

class Graph(
    val centers: CenterList,
    val corners: List<Corner>,
    val edges: EdgeList
) {
    val cornerList = CornerList(corners.size)
}