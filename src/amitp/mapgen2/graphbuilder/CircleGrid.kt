package amitp.mapgen2.graphbuilder

import amitp.mapgen2.structures.CellList
import amitp.mapgen2.structures.CornerList
import amitp.mapgen2.structures.EdgeList
import amitp.mapgen2.structures.PackedIntLists
import me.anno.maths.Packing.pack64
import me.anno.maths.Packing.unpackHighFrom64
import me.anno.maths.Packing.unpackLowFrom64
import me.anno.utils.Clock
import me.anno.utils.types.Floats.toIntOr
import org.joml.AABBf
import kotlin.math.*

class CircleGrid(
    val numPointsX: Int,
    val numPointsY: Int,
    bounds: AABBf
) {

    val minX = bounds.minX
    val minY = bounds.minY
    val invX = numPointsX / max(bounds.deltaX, 1e-38f)
    val invY = numPointsY / max(bounds.deltaY, 1e-38f)

    val gridSize = numPointsX * numPointsY
    val circleData = FloatArray(gridSize * 3)

    // x,y
    val cellData = FloatArray(gridSize * 2)

    init {
        // fill field with distanced points
        cellData.fill(-1e9f)
    }

    val centerIndices = PackedIntLists(gridSize, 4)

    var statsOutOfBounds = 0
    var statsCovered = 0
    var statsFoundSelf = 0
    var statsNormal = 0

    fun addCell(cellX: Float, cellY: Float) {

        val gx = ((cellX - minX) * invX).toIntOr(-1)
        val gy = ((cellY - minY) * invY).toIntOr(-1)

        if (gx < 0 || gy < 0 || gx >= numPointsX || gy >= numPointsY) {
            // circle out of bounds
            statsOutOfBounds++
            return
        }

        val gridIndex = gx + gy * numPointsX
        cellData[gridIndex * 2] = cellX
        cellData[gridIndex * 2 + 1] = cellY

    }

    fun addCircle(
        circleX: Float, circleY: Float, circleRadiusSq: Float,
        cellA: Int, cellB: Int, cellC: Int
    ) {

        val gx = ((circleX - minX) * invX).toIntOr(-1)
        val gy = ((circleY - minY) * invY).toIntOr(-1)

        if (gx < 0 || gy < 0 || gx >= numPointsX || gy >= numPointsY) {
            // circle out of bounds
            statsOutOfBounds++
            return
        }

        // check whether out corner already exists
        val minX0 = max(gx - 1, 0)
        val minY0 = max(gy - 1, 0)
        val maxX0 = min(gx + 1, numPointsX - 1)
        val maxY0 = min(gy + 1, numPointsY - 1)

        // todo rounding and 2x2 field would be good enough, too
        // no circle must contain the center of another circle
        for (y in minY0..maxY0) {
            for (x in minX0..maxX0) {
                val gridIndex = x + y * numPointsX
                val gridCircleX = circleData[gridIndex * 3]
                val gridCircleY = circleData[gridIndex * 3 + 1]
                val gridRadiusSq = circleData[gridIndex * 3 + 2]
                if (gridRadiusSq <= 0f) continue

                val dx = gridCircleX - circleX
                val dy = gridCircleY - circleY
                if (abs(dx) < invX && abs(dy) < invY && gridRadiusSq / circleRadiusSq in 0.95f..1.05f) {
                    // if the circle is identical, don't delete it...
                    statsFoundSelf++
                    return addToCircle(gridIndex, cellA, cellB, cellC)
                }
            }
        }

        val circleRadius = sqrt(circleRadiusSq)
        val dx = ceil(circleRadius * invX).toIntOr()
        val dy = ceil(circleRadius * invY).toIntOr()

        // check whether there is any cell inside of us
        val minX1 = max(gx - dx, 0)
        val minY1 = max(gy - dy, 0)
        val maxX1 = min(gx + dx, numPointsX - 1)
        val maxY1 = min(gy + dy, numPointsY - 1)

        // no circle must contain the center of another circle
        for (y in minY1..maxY1) {
            for (x in minX1..maxX1) {
                val gridIndex = x + y * numPointsX
                val gridCircleX = cellData[gridIndex * 2]
                val gridCircleY = cellData[gridIndex * 2 + 1]
                val dx = gridCircleX - circleX
                val dy = gridCircleY - circleY

                val distanceSq = 1.01f * (dx * dx + dy * dy)
                if (circleRadiusSq >= distanceSq) {
                    // our circle is too large
                    // todo when we remove this, some holes get covered up,
                    //  but we DO need this function, don't we?
                    statsCovered++
                    return
                }
            }
        }

        statsNormal++
        val gridIndex = gx + gy * numPointsX
        return createCircle(gridIndex, circleX, circleY, circleRadiusSq, cellA, cellB, cellC)
    }

    private fun createCircle(
        gridIndex: Int,
        circleX: Float, circleY: Float, circleRadiusSq: Float,
        cellA: Int, cellB: Int, cellC: Int
    ) {
        circleData[gridIndex * 3] = circleX
        circleData[gridIndex * 3 + 1] = circleY
        circleData[gridIndex * 3 + 2] = circleRadiusSq
        addToCircle(gridIndex, cellA, cellB, cellC)
    }

    private fun addToCircle(
        gridIndex: Int,
        cellA: Int, cellB: Int, cellC: Int
    ) {
        centerIndices.addUnique(gridIndex, cellA)
        centerIndices.addUnique(gridIndex, cellB)
        centerIndices.addUnique(gridIndex, cellC)
    }

    /*
    Wanted state:
[07:45:10.538,INFO:VoronoiGraphBuilder] Stats: [10000 cells + 19970 corners + 29969 edges]
[07:45:10.539,INFO:VoronoiGraphBuilder] Cells.neighbors: 59938
[07:45:10.539,INFO:VoronoiGraphBuilder] Cells.corners: 59910
[07:45:10.540,INFO:VoronoiGraphBuilder] Cells.edges: 59938
[07:45:10.541,INFO:VoronoiGraphBuilder] Corners.cells: 59910
[07:45:10.543,INFO:VoronoiGraphBuilder] Corners.adjacent: 59882
[07:45:10.544,INFO:VoronoiGraphBuilder] Corners.edges: 59910

    Current state:
[07:43:28.869,INFO:VoronoiGraphBuilder] Stats: [10000 cells + 8322 corners + 21698 edges]
[07:43:28.869,INFO:VoronoiGraphBuilder] Cells.neighbors: 43396
[07:43:28.870,INFO:VoronoiGraphBuilder] Cells.corners: 24966
[07:43:28.871,INFO:VoronoiGraphBuilder] Cells.edges: 43396
[07:43:28.872,INFO:VoronoiGraphBuilder] Corners.cells: 24966
[07:43:28.872,INFO:VoronoiGraphBuilder] Corners.adjacent: 6536
[07:43:28.873,INFO:VoronoiGraphBuilder] Corners.edges: 24966
    * */

    fun extractCornersAndEdges(cells: CellList, clock: Clock): CornersAndEdges {

        println(
            "Stats: { normal: $statsNormal, found: $statsFoundSelf, " +
                    "covered: $statsCovered, " +
                    "outOfBounds: $statsOutOfBounds }"
        )

        val gridToCorner = IntArray(gridSize)
        gridToCorner.fill(-1)

        var numCorners = 0
        for (gridIndex in 0 until gridSize) {
            val radiusSq = circleData[gridIndex * 3 + 2]
            if (radiusSq <= 0f) continue
            gridToCorner[gridIndex] = numCorners++
        }
        clock.stop("Count corners")

        val corners = CornerList(numCorners)

        for (gridIndex in 0 until gridSize) {
            val corner = gridToCorner[gridIndex]
            if (corner < 0) continue

            val cx = circleData[gridIndex * 3]
            val cy = circleData[gridIndex * 3 + 1]
            corners.setPoint(corner, cx, cy)
        }
        clock.stop("Corner positions")

        for (cellIndex in 0 until gridSize) {
            val corner = gridToCorner[cellIndex]
            if (corner < 0) continue

            centerIndices.forEach(cellIndex) { center ->
                corners.cells.add(corner, center)
                cells.corners.addUnique(center, corner)
            }
        }
        clock.stop("Corners.cells")

        val uniqueEdges = HashMap<Long, Int>()
        for (gridIndex in 0 until gridSize) {
            val corner = gridToCorner[gridIndex]
            if (corner < 0) continue

            val numCenters = centerIndices.getSize(gridIndex)
            for (ci in 1 until numCenters) {
                for (cj in 0 until ci) {
                    val ai = centerIndices[gridIndex, ci]
                    val bi = centerIndices[gridIndex, cj]
                    val key = pack64(min(ai, bi), max(ai, bi))
                    val edge = uniqueEdges.getOrPut(key) { uniqueEdges.size }
                    corners.edges.add(corner, edge)
                    cells.neighbors.addUnique(ai, bi)
                    cells.neighbors.addUnique(bi, ai)
                    cells.edges.addUnique(ai, edge)
                    cells.edges.addUnique(bi, edge)
                }
            }
        }
        clock.stop("Find Edges")

        val edges = EdgeList(uniqueEdges.size)
        for ((key, edge) in uniqueEdges) {
            val ai = unpackHighFrom64(key)
            val bi = unpackLowFrom64(key)
            edges.setCellA(edge, ai)
            edges.setCellB(edge, bi)
        }
        clock.stop("Edges.setCellAB")

        for (edge in 0 until edges.size) {
            edges.setCornerA(edge, -1)
            edges.setCornerB(edge, -1)
        }

        for (gridIndex in 0 until gridSize) {
            val corner = gridToCorner[gridIndex]
            if (corner < 0) continue

            val numCenters = centerIndices.getSize(gridIndex)
            for (ci in 1 until numCenters) {
                for (cj in 0 until ci) {
                    val ai = centerIndices[gridIndex, ci]
                    val bi = centerIndices[gridIndex, cj]
                    val key = pack64(min(ai, bi), max(ai, bi))
                    val edge = uniqueEdges[key] ?: throw IllegalStateException("Edge must have been seen before")
                    if (edges.getCornerA(edge) == -1) {
                        edges.setCornerA(edge, corner)
                    } else {
                        edges.setCornerB(edge, corner)
                    }
                }
            }
        }
        clock.stop("Edges.setCornerAB")

        return CornersAndEdges(corners, edges)
    }


}