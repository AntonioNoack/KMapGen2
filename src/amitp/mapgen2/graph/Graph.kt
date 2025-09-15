package amitp.mapgen2.graph

import amitp.mapgen2.geometry.CenterList
import amitp.mapgen2.geometry.CornerList
import amitp.mapgen2.geometry.EdgeList

class Graph(
    val centers: CenterList,
    val corners: CornerList,
    val edges: EdgeList,
)