package amitp.mapgen2

import amitp.mapgen2.decoration.NoisyEdges
import me.anno.utils.algorithms.ForLoop.forLoopSafely
import java.awt.Color
import java.awt.RenderingHints
import java.awt.image.BufferedImage

fun renderNoisyEdges(noisyEdges: NoisyEdges, mapSize: Float): BufferedImage {
    val img = BufferedImage(mapSize.toInt(), mapSize.toInt(), BufferedImage.TYPE_INT_ARGB)
    val g = img.createGraphics()

    // Optional: anti-aliasing
    g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)

    g.color = Color.BLACK
    g.fillRect(0, 0, img.width, img.height)

    val offsets = noisyEdges.offsets
    val points = noisyEdges.points

    g.color = Color.WHITE
    for (i in 1 until offsets.size) {
        val i0 = offsets[i - 1]
        val i1 = offsets[i]

        forLoopSafely(i1 - i0 - 2, 2) { di ->
            val i = i0 + di
            g.drawLine(
                points[i].toInt(),
                points[i + 1].toInt(),
                points[i + 2].toInt(),
                points[i + 3].toInt()
            )
        }
    }

    g.dispose()
    return img
}
