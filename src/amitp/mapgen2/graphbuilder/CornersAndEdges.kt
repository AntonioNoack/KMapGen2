package amitp.mapgen2.graphbuilder

import amitp.mapgen2.structures.CornerList
import amitp.mapgen2.structures.EdgeList

data class CornersAndEdges(
    val corners: CornerList,
    val edges: EdgeList
)