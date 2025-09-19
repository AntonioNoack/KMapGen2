package amitp.mapgen2

import amitp.mapgen2.structures.CornerList
import org.joml.Vector2f
import java.awt.BasicStroke
import java.awt.Color
import java.awt.RenderingHints
import java.awt.image.BufferedImage

fun renderBiomeMap(map: GeneratedMap, mapSize: Vector2f): BufferedImage {

    val img = BufferedImage(mapSize.x.toInt(), mapSize.y.toInt(), BufferedImage.TYPE_INT_ARGB)
    val g = img.createGraphics()

    // Optional: anti-aliasing
    g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)

    g.color = Color.BLACK
    g.fillRect(0, 0, img.width, img.height)

    val edges = map.edges
    val corners = map.corners
    for (index in edges.indices) {
        val v0 = corners.getOrNull(edges.getCornerA(index)) ?: continue
        val v1 = corners.getOrNull(edges.getCornerB(index)) ?: continue

        g.color = Color.WHITE
        g.drawLine(v0.x.toInt(), v0.y.toInt(), v1.x.toInt(), v1.y.toInt())
    }

    // println("Cells: ${map.cells!!.size}")
    val cells = map.cells
    val xs = IntArray(16)
    val ys = IntArray(16)
    val biomeColors = Biome.entries.map { Color(it.debugColor) }
    for (i in cells.indices) {
        g.color = biomeColors[cells.getBiome(i).ordinal]

        var n = 0
        cells.corners.forEach(i) { c ->
            xs[n] = corners.getPointX(c).toInt()
            ys[n] = corners.getPointY(c).toInt()
            n++
        }

        // Skip cells with < 3 corners (degenerate)
        if (n < 3) continue

        g.fillPolygon(xs, ys, n)

        // Optional: draw edges in black for visibility
        g.color = Color.BLACK
        g.drawPolygon(xs, ys, n)
    }

    g.color = Color.DARK_GRAY
    g.stroke = BasicStroke(3f)
    for (edgeIndex in edges.indices) {
        if (edges.isRoad(edgeIndex)) {
            val v0 = corners.getOrNull(edges.getCornerA(edgeIndex)) ?: continue
            val v1 = corners.getOrNull(edges.getCornerB(edgeIndex)) ?: continue
            g.drawLine(v0.x.toInt(), v0.y.toInt(), v1.x.toInt(), v1.y.toInt())
        }
    }

    g.color = Color.CYAN
    g.stroke = BasicStroke(2f)
    for (edgeIndex in edges.indices) {
        if (edges.hasRiver(edgeIndex)) {
            val v0 = corners.getOrNull(edges.getCornerA(edgeIndex)) ?: continue
            val v1 = corners.getOrNull(edges.getCornerB(edgeIndex)) ?: continue
            g.drawLine(v0.x.toInt(), v0.y.toInt(), v1.x.toInt(), v1.y.toInt())
        }
    }

    g.color = Color.ORANGE
    g.stroke = BasicStroke(2f)
    for (edgeIndex in edges.indices) {
        if (edges.hasLava(edgeIndex)) {
            val v0 = corners.getOrNull(edges.getCornerA(edgeIndex)) ?: continue
            val v1 = corners.getOrNull(edges.getCornerB(edgeIndex)) ?: continue
            g.drawLine(v0.x.toInt(), v0.y.toInt(), v1.x.toInt(), v1.y.toInt())
        }
    }

    g.dispose()
    return img

}

fun CornerList.getOrNull(i: Int): Vector2f? {
    return if (i < 0) null else Vector2f(getPointX(i), getPointY(i))
}

