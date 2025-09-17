package amitp.mapgen2

import me.anno.maths.Maths.clamp
import java.awt.Color
import java.awt.RenderingHints
import java.awt.image.BufferedImage

fun renderHeightMap(map: GeneratedMap, size: Int): BufferedImage {
    val image = BufferedImage(size, size, BufferedImage.TYPE_INT_RGB)
    val g = image.createGraphics()

    // Optional: anti-aliasing
    g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)

    val cells = map.cells
    val corners = map.corners
    val xs = IntArray(16)
    val ys = IntArray(16)
    val colors = Array(256) { Color(it, it, it) }
    for (i in 0 until cells.size) {

        // Compute polygon centroid
        var avgElevation = 0f
        var n = 0
        cells.corners.forEach(i) { c ->
            xs[n] = corners.getPointX(c).toInt()
            ys[n] = corners.getPointY(c).toInt()
            avgElevation += corners.getElevation(c)
            n++
        }
        if (n < 3) continue

        // Average elevation for this polygon
        avgElevation = clamp(avgElevation / n)
        if (cells.isOcean(i) || cells.isCoast(i)) continue

        g.color = colors[(avgElevation * 255).toInt()]
        g.fillPolygon(xs, ys, n)
    }

    g.dispose()
    return image
}
