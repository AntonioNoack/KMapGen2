package amitp.mapgen2.graph

import amitp.mapgen2.geometry.Center
import amitp.mapgen2.geometry.Corner
import amitp.mapgen2.geometry.EdgeList

class Graph(
    val centers: List<Center>,
    val corners: List<Corner>,
    val edges: EdgeList
)