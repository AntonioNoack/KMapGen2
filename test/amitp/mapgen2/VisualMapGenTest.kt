package amitp.mapgen2

import amitp.mapgen2.geometry.Corner
import amitp.mapgen2.graph.Graph
import amitp.mapgen2.graph.VoronoiGraphBuilder
import amitp.mapgen2.pointselector.GridPointSelector
import amitp.mapgen2.pointselector.RandomPointSelector
import amitp.mapgen2.shape.PerlinIslandShape
import org.joml.Vector2f
import java.awt.Color
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO
import kotlin.math.atan2

fun main() {

    val mapSize = 1000f
    val numPoints = 4000
    val variant = 4284L

    val map = MapGen2.generate(
        mapSize,
        PerlinIslandShape(8541),
        if (true) VoronoiGraphBuilder(RandomPointSelector)
        else VoronoiGraphBuilder(GridPointSelector(0.3f)),
        numPoints, variant
    )

    val img = BufferedImage(mapSize.toInt(), mapSize.toInt(), BufferedImage.TYPE_INT_ARGB)
    val g = img.createGraphics()

    // Optional: anti-aliasing
    g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)

    g.color = Color.BLACK
    g.fillRect(0, 0, img.width, img.height)

    val edges = map.edges
    val corners = map.corners
    for (index in 0 until edges.size) {
        val v0 = corners.getOrNull(edges.getV0(index))?.point ?: continue
        val v1 = corners.getOrNull(edges.getV1(index))?.point ?: continue

        g.color = Color.WHITE
        g.drawLine(v0.x.toInt(), v0.y.toInt(), v1.x.toInt(), v1.y.toInt())
    }

    // println("Centers: ${map.centers!!.size}")
    val centers = map.centers
    for (i in 0 until centers.size) {
        val biomeColor = biomeColors[centers.getBiome(i)] ?: Color.LIGHT_GRAY
        g.color = biomeColor

        val corners1 = ArrayList<Vector2f>()
        centers.corners.forEach(i) { c ->
            corners1.add(corners[c].point)
        }

        // Skip centers with < 3 corners (degenerate)
        if (corners1.size < 3) continue
        val cx = corners1.map { it.x }.average()
        val cy = corners1.map { it.y }.average()

        val sortedCorners = corners1.sortedBy {
            val dx = it.x - cx
            val dy = it.y - cy
            atan2(dy, dx)
        }

        val xPoints = sortedCorners.map { it.x.toInt() }.toIntArray()
        val yPoints = sortedCorners.map { it.y.toInt() }.toIntArray()

        g.fillPolygon(xPoints, yPoints, corners1.size)

        // Optional: draw edges in black for visibility
        g.color = Color.BLACK
        g.drawPolygon(xPoints, yPoints, corners1.size)
    }

    for (index in 0 until edges.size) {
        if (edges.hasRivers(index)) {
            val v0 = corners.getOrNull(edges.getV0(index))?.point ?: continue
            val v1 = corners.getOrNull(edges.getV1(index))?.point ?: continue

            g.color = Color.CYAN
            g.drawLine(v0.x.toInt(), v0.y.toInt(), v1.x.toInt(), v1.y.toInt())
        }
    }

    val home = System.getProperty("user.home")
    ImageIO.write(img, "PNG", File(home, "Desktop/test_map.png"))

    val img2 = renderHeightMap(map, 1024)
    ImageIO.write(img2, "PNG", File(home, "Desktop/test_height.png"))


}

fun renderHeightMap(map: Graph, size: Int): BufferedImage {
    val image = BufferedImage(size, size, BufferedImage.TYPE_INT_RGB)
    val g = image.createGraphics()

    // Optional: anti-aliasing
    g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)

    val centers = map.centers
    val corners = map.corners
    for (i in 0 until centers.size) {

        // Compute polygon centroid
        val corners1 = ArrayList<Corner>()
        centers.corners.forEach(i) { c ->
            corners1.add(corners[c])
        }
        if (corners1.size < 3) continue
        val cx = corners1.map { it.point.x }.average()
        val cy = corners1.map { it.point.y }.average()

        // Sort corners clockwise
        val sortedCorners = corners1.sortedBy {
            val dx = it.point.x - cx
            val dy = it.point.y - cy
            atan2(dy, dx)
        }

        // Average elevation for this polygon
        val avgElevation = corners1.map { it.elevation }.average().coerceIn(0.0, 1.0)

        val gray = if (centers.isOcean(i) || centers.isCoast(i)) 12
        else (avgElevation * 255).toInt().coerceIn(0, 255)
        g.color = Color(gray, gray, gray)

        val xPoints = sortedCorners.map { it.point.x.toInt() }.toIntArray()
        val yPoints = sortedCorners.map { it.point.y.toInt() }.toIntArray()
        g.fillPolygon(xPoints, yPoints, sortedCorners.size)
    }

    g.dispose()
    return image
}

val biomeColors = mapOf(
    Biome.OCEAN to Color(0, 105, 148),
    Biome.BEACH to Color(238, 214, 175),
    Biome.GRASSLAND to Color(124, 252, 0),
    Biome.TROPICAL_RAIN_FOREST to Color(0, 100, 0),
    Biome.TROPICAL_SEASONAL_FOREST to Color(34, 139, 34),
    Biome.TEMPERATE_RAIN_FOREST to Color(0, 128, 0),
    Biome.TEMPERATE_DECIDUOUS_FOREST to Color(34, 139, 34),
    Biome.SHRUBLAND to Color(160, 82, 45),
    Biome.TAIGA to Color(0, 100, 0),
    Biome.TEMPERATE_DESERT to Color(210, 180, 140),
    Biome.SUBTROPICAL_DESERT to Color(237, 201, 175),
    Biome.SNOW to Color(255, 250, 250),
    Biome.TUNDRA to Color(176, 196, 222),
    Biome.BARE to Color(139, 137, 137),
    Biome.SCORCHED to Color(80, 80, 80),
    Biome.LAKE to Color(28, 107, 160),
    Biome.MARSH to Color(47, 79, 79),
    Biome.ICE to Color(180, 220, 255)
)
