package amitp.mapgen2.graphbuilder.voronoi

import amitp.mapgen2.structures.CellList
import amitp.mapgen2.structures.CornerList
import amitp.mapgen2.structures.EdgeList
import me.anno.maths.Maths.clamp
import me.anno.maths.Packing.pack64
import me.anno.maths.Packing.unpackHighFrom64
import me.anno.maths.Packing.unpackLowFrom64
import me.anno.utils.Clock
import me.anno.utils.algorithms.ForLoop.forLoopSafely
import me.anno.utils.structures.arrays.FloatArrayList
import me.anno.utils.structures.arrays.FloatArrayListUtils.add
import me.anno.utils.structures.arrays.IntArrayList
import org.joml.AABBf
import kotlin.collections.iterator
import kotlin.math.*

class CircleGrid(
    val numCellsX: Int,
    val numCellsY: Int,
    bounds: AABBf
) {

    companion object {
        private const val DISTANCE_ACCURACY = 1.01f
    }

    class Circumcircle(val x: Float, val y: Float, val rSq: Float) {
        val cells = IntArrayList(4)
        var next: Circumcircle? = null
    }

    val minX = bounds.minX
    val minY = bounds.minY
    val invX = numCellsX / max(bounds.deltaX, 1e-38f)
    val invY = numCellsY / max(bounds.deltaY, 1e-38f)

    val gridSize = numCellsX * numCellsY
    val circumcircleGrid = arrayOfNulls<Circumcircle>(gridSize)

    // x,y
    val cellGrid = FloatArray(gridSize * 2)
    val extraCells = FloatArrayList()
    val controlCells = FloatArrayList()

    val invalidCellPos = -1e9f

    init {
        // fill field with distanced points
        cellGrid.fill(invalidCellPos)
    }

    var statsOutOfBounds = 0
    var statsCovered = 0
    var statsFoundSelf = 0
    var statsNormal = 0

    fun addCell(cellX: Float, cellY: Float) {
        val gx = clamp(((cellX - minX) * invX).toInt(), 0, numCellsX - 1)
        val gy = clamp(((cellY - minY) * invY).toInt(), 0, numCellsY - 1)
        val gridIndex = gx + gy * numCellsX
        if (cellGrid[gridIndex * 2] != invalidCellPos) {

            // look for a nearby empty cell
            for (gyi in gy - 1..gy + 1) {
                for (gxi in gx - 1..gx + 1) {
                    val gridIndexI = gxi + gyi * numCellsX
                    if (cellGrid[gridIndexI * 2] == invalidCellPos) {
                        cellGrid[gridIndexI * 2] = cellX
                        cellGrid[gridIndexI * 2 + 1] = cellY

                        // println("Inserting #${controlCells.size shr 1}, $cellX,$cellY into $gxi,$gyi")
                        controlCells.add(cellX, cellY)
                        return
                    }
                }
            }

            extraCells.add(cellX, cellY)
            println("Overriding cell! $gridIndex (${extraCells.size shr 1} total)")
            // println("Inserting #${controlCells.size shr 1}, $cellX,$cellY into extras")
        } else {
            cellGrid[gridIndex * 2] = cellX
            cellGrid[gridIndex * 2 + 1] = cellY
            // println("Inserting #${controlCells.size shr 1}, $cellX,$cellY into $gx,$gy")
        }

        // all is fine -> no control cells needed any more
        // controlCells.add(cellX, cellY)
    }

    fun IntArrayList.addUnique(value: Int) {
        if (value !in this) add(value)
    }

    fun addCircumcircle(
        circleX: Float, circleY: Float, circleRadiusSq: Float,
        cellA: Int, cellB: Int, cellC: Int
    ) {

        val gx = clamp(((circleX - minX) * invX).toInt(), 0, numCellsX - 1)
        val gy = clamp(((circleY - minY) * invY).toInt(), 0, numCellsY - 1)

        // check whether out corner already exists
        val minX0 = max(gx - 1, 0)
        val minY0 = max(gy - 1, 0)
        val maxX0 = min(gx + 1, numCellsX - 1)
        val maxY0 = min(gy + 1, numCellsY - 1)

        val watched = setOf(cellA, cellB, cellC) == setOf(2, 8, 36)
        if (watched) println("Found watched cell: $circleX,$circleY, radius ${sqrt(circleRadiusSq)}")

        // todo rounding and 2x2 field would be good enough, too
        // no circle must contain the center of another circle
        val epsilon = 1e-3f // todo make depend on scale
        for (y in minY0..maxY0) {
            for (x in minX0..maxX0) {
                val gridIndex = x + y * numCellsX
                var gridCircle = circumcircleGrid[gridIndex]
                while (gridCircle != null) {

                    val gridCircleX = gridCircle.x
                    val gridCircleY = gridCircle.y
                    val gridRadiusSq = gridCircle.rSq
                    if (gridRadiusSq <= 0f) continue

                    val dx = gridCircleX - circleX
                    val dy = gridCircleY - circleY
                    if (abs(dx) < epsilon &&
                        abs(dy) < epsilon &&
                        gridRadiusSq / circleRadiusSq in 0.999f..1.001f
                    ) {
                        statsFoundSelf++
                        addToCircumcircle(gridCircle, cellA, cellB, cellC)
                        if (watched) println("found self")
                        return
                    }

                    gridCircle = gridCircle.next
                }
            }
        }

        // check whether there is any cell inside of us
        val circleRadius = sqrt(circleRadiusSq)
        val minX1 = max(floor((circleX - circleRadius - minX) * invX).toInt() - 1, 0)
        val minY1 = max(floor((circleY - circleRadius - minY) * invY).toInt() - 1, 0)
        val maxX1 = min(ceil((circleX + circleRadius - minX) * invX).toInt(), numCellsX - 1)
        val maxY1 = min(ceil((circleY + circleRadius - minY) * invY).toInt(), numCellsY - 1)

        // no circle must contain the center of another circle
        // println("Checking cells...")
        for (y in minY1..maxY1) {
            for (x in minX1..maxX1) {
                val gridIndex = x + y * numCellsX
                val gridCircleX = cellGrid[gridIndex * 2]
                val gridCircleY = cellGrid[gridIndex * 2 + 1]
                val dx = gridCircleX - circleX
                val dy = gridCircleY - circleY

                val distanceSq = DISTANCE_ACCURACY * (dx * dx + dy * dy)
                if (circleRadiusSq >= distanceSq) {
                    // our circle is too large
                    statsCovered++
                    if (watched) println("  $cellA,$cellB,$cellC is covered by $gridCircleX,$gridCircleY")
                    return
                }

                // println("  passed $x,$y -> ${circleRadiusSq / distanceSq}")
            }
        }

        forLoopSafely(extraCells.size, 2) { i ->
            val gridCircleX = extraCells[i]
            val gridCircleY = extraCells[i + 1]
            val dx = gridCircleX - circleX
            val dy = gridCircleY - circleY

            val distanceSq = DISTANCE_ACCURACY * (dx * dx + dy * dy)
            if (circleRadiusSq >= distanceSq) {
                // our circle is too large
                statsCovered++
                if (watched) println("  $cellA,$cellB,$cellC is covered by $gridCircleX,$gridCircleY")
                return
            }
        }

        forLoopSafely(controlCells.size, 2) { i ->
            val gridCircleX = controlCells[i]
            val gridCircleY = controlCells[i + 1]
            val dxi = gridCircleX - circleX
            val dyi = gridCircleY - circleY

            val distanceSq = DISTANCE_ACCURACY * (dxi * dxi + dyi * dyi)
            if (circleRadiusSq >= distanceSq) {
                println("ScanX: $minX1..$maxX1, $minY1..$maxY1 by $gx,$gy by ${sqrt(circleRadiusSq)}*($invX,$invY)")
                throw IllegalStateException("Missed circle #${i / 2} $gridCircleX,$gridCircleY,$distanceSq that would have covered the circumcenter")
            }
        }

        statsNormal++

        val newCircle = Circumcircle(circleX, circleY, circleRadiusSq)
        addToCircumcircle(newCircle, cellA, cellB, cellC)

        val gridIndex = gx + gy * numCellsX
        newCircle.next = circumcircleGrid[gridIndex]
        circumcircleGrid[gridIndex] = newCircle
    }

    private fun addToCircumcircle(
        circle: Circumcircle,
        cellA: Int, cellB: Int, cellC: Int
    ) {
        val cells = circle.cells
        cells.addUnique(cellA)
        cells.addUnique(cellB)
        cells.addUnique(cellC)
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

    fun interface CornerCallback {
        fun call(corner: Int, circumcircle: Circumcircle)
    }

    fun forEachCorner(callback: CornerCallback): Int {
        var corner = 0
        for (gridIndex in circumcircleGrid.indices) {
            var circumcircle = circumcircleGrid[gridIndex]
            while (circumcircle != null) {
                callback.call(corner, circumcircle)
                corner++

                // fetch next circumcircle
                circumcircle = circumcircle.next
            }
        }
        return corner
    }

    fun extractCornersAndEdges(cells: CellList, clock: Clock): CornersAndEdges {

        println(
            "Stats: { normal: $statsNormal, found: $statsFoundSelf, " +
                    "covered: $statsCovered, " +
                    "outOfBounds: $statsOutOfBounds }"
        )

        val numCorners = forEachCorner { _, _ -> }
        clock.stop("Count corners")

        val corners = CornerList(numCorners)
        forEachCorner { corner, circumcircle ->
            corners.setPoint(corner, circumcircle.x, circumcircle.y)
        }
        clock.stop("Corner positions")

        forEachCorner { corner, circumcircle ->
            val cellsAtCorner = circumcircle.cells
            for (i in cellsAtCorner.indices) {
                val center = cellsAtCorner[i]
                corners.cells.add(corner, center)
                cells.corners.addUnique(center, corner)
            }
        }
        clock.stop("Corners.cells")

        val uniqueEdges = HashMap<Long, Int>()
        forEachCorner { corner, circumcircle ->
            val cellsAtCorner = circumcircle.cells
            for (ci in 1 until cellsAtCorner.size) {
                for (cj in 0 until ci) {
                    val ai = cellsAtCorner[ci]
                    val bi = cellsAtCorner[cj]
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

        for (edge in edges.indices) {
            edges.setCornerA(edge, -1)
            edges.setCornerB(edge, -1)
        }

        forEachCorner { corner, circumcircle ->
            val cellsAtCorner = circumcircle.cells
            for (ci in 1 until cellsAtCorner.size) {
                for (cj in 0 until ci) {
                    val ai = cellsAtCorner[ci]
                    val bi = cellsAtCorner[cj]
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