package amitp.mapgen2

import amitp.mapgen2.decoration.Lava
import amitp.mapgen2.decoration.NoisyEdges
import amitp.mapgen2.decoration.Roads
import amitp.mapgen2.graphbuilder.VoronoiGraphBuilder
import amitp.mapgen2.islandshapes.PerlinIslandShape
import amitp.mapgen2.pointselector.GridPointSelector
import amitp.mapgen2.pointselector.RandomPointSelector
import amitp.mapgen2.structures.CornerList
import me.anno.maths.Maths.clamp
import me.anno.utils.Clock
import me.anno.utils.algorithms.ForLoop.forLoopSafely
import org.joml.Vector2f
import java.awt.BasicStroke
import java.awt.Color
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO
import kotlin.math.atan2
import kotlin.random.Random

fun main() {

    val mapSize = 4000f
    val numPoints = 10000
    val variant = 4284L

    val clock = Clock("VisualTest")

    val map = MapGen2.generate(
        mapSize,
        PerlinIslandShape(8541),
        if (false) VoronoiGraphBuilder(RandomPointSelector)
        else VoronoiGraphBuilder(GridPointSelector(0.7f)),
        numPoints, variant
    )
    clock.stop("Generating Map")

    Roads.generateRoads(map)
    clock.stop("Generate Roads")

    Lava.generateLava(map, Random(variant))
    clock.stop("Generate Lava")

    val home = System.getProperty("user.home")
    ImageIO.write(renderBiomeMap(map, mapSize), "PNG", File(home, "Desktop/test_map.png"))
    clock.stop("Render BiomeMap")

    ImageIO.write(renderHeightMap(map, mapSize.toInt()), "PNG", File(home, "Desktop/test_height.png"))
    clock.stop("Render HeightMap")

    if (false) {// disabled, because too slow at the moment
        val noisyEdges = NoisyEdges.generateNoisyEdges(map, Random(variant))
        clock.stop("Generate NoisyEdges")
        ImageIO.write(renderNoisyEdges(noisyEdges, mapSize), "PNG", File(home, "Desktop/test_edges.png"))
        clock.stop("Render NoisyEdges")
    }

}

fun CornerList.getOrNull(i: Int): Vector2f? {
    return if (i < 0) null else Vector2f(getPointX(i), getPointY(i))
}

fun renderNoisyEdges(noisyEdges: NoisyEdges, mapSize: Float): BufferedImage {
    val scale = 10f
    val img = BufferedImage((mapSize * scale).toInt(), (mapSize * scale).toInt(), BufferedImage.TYPE_INT_ARGB)
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
        if (i1 - i0 > 100) {
            println("Too long segment! $i0-$i1")
            continue
        }

        forLoopSafely(i1 - i0 - 2, 2) { di ->
            val i = i0 + di
            g.drawLine(
                (points[i] * scale).toInt(),
                (points[i + 1] * scale).toInt(),
                (points[i + 2] * scale).toInt(),
                (points[i + 3] * scale).toInt()
            )
        }
    }

    g.dispose()
    return img
}

fun renderBiomeMap(map: GeneratedMap, mapSize: Float): BufferedImage {

    val img = BufferedImage(mapSize.toInt(), mapSize.toInt(), BufferedImage.TYPE_INT_ARGB)
    val g = img.createGraphics()

    // Optional: anti-aliasing
    g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)

    g.color = Color.BLACK
    g.fillRect(0, 0, img.width, img.height)

    val edges = map.edges
    val corners = map.corners
    for (index in 0 until edges.size) {
        val v0 = corners.getOrNull(edges.getCornerA(index)) ?: continue
        val v1 = corners.getOrNull(edges.getCornerB(index)) ?: continue

        g.color = Color.WHITE
        g.drawLine(v0.x.toInt(), v0.y.toInt(), v1.x.toInt(), v1.y.toInt())
    }

    // println("Cells: ${map.cells!!.size}")
    val cells = map.cells
    for (i in 0 until cells.size) {
        val biomeColor = biomeColors[cells.getBiome(i)] ?: Color.LIGHT_GRAY
        g.color = biomeColor

        val corners1 = ArrayList<Vector2f>()
        cells.corners.forEach(i) { c ->
            corners1.add(corners.getOrNull(c)!!)
        }

        // Skip cells with < 3 corners (degenerate)
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

    g.color = Color.DARK_GRAY
    g.stroke = BasicStroke(3f)
    for (edgeIndex in 0 until edges.size) {
        if (edges.hasRoad(edgeIndex)) {
            val v0 = corners.getOrNull(edges.getCornerA(edgeIndex)) ?: continue
            val v1 = corners.getOrNull(edges.getCornerB(edgeIndex)) ?: continue
            g.drawLine(v0.x.toInt(), v0.y.toInt(), v1.x.toInt(), v1.y.toInt())
        }
    }

    g.color = Color.CYAN
    g.stroke = BasicStroke(2f)
    for (edgeIndex in 0 until edges.size) {
        if (edges.hasRiver(edgeIndex)) {
            val v0 = corners.getOrNull(edges.getCornerA(edgeIndex)) ?: continue
            val v1 = corners.getOrNull(edges.getCornerB(edgeIndex)) ?: continue
            g.drawLine(v0.x.toInt(), v0.y.toInt(), v1.x.toInt(), v1.y.toInt())
        }
    }

    g.color = Color.ORANGE
    g.stroke = BasicStroke(2f)
    for (edgeIndex in 0 until edges.size) {
        if (edges.hasLava(edgeIndex)) {
            val v0 = corners.getOrNull(edges.getCornerA(edgeIndex)) ?: continue
            val v1 = corners.getOrNull(edges.getCornerB(edgeIndex)) ?: continue
            g.drawLine(v0.x.toInt(), v0.y.toInt(), v1.x.toInt(), v1.y.toInt())
        }
    }

    g.dispose()
    return img

}

fun renderHeightMap(map: GeneratedMap, size: Int): BufferedImage {
    val image = BufferedImage(size, size, BufferedImage.TYPE_INT_RGB)
    val g = image.createGraphics()

    // Optional: anti-aliasing
    g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)

    val cells = map.cells
    val corners = map.corners
    for (i in 0 until cells.size) {

        // Compute polygon centroid
        val corners1 = ArrayList<Vector2f>()
        var avgElevation = 0f
        cells.corners.forEach(i) { c ->
            corners1.add(corners.getOrNull(c)!!)
            avgElevation += corners.getElevation(c)
        }
        if (corners1.size < 3) continue
        val cx = corners1.map { it.x }.average()
        val cy = corners1.map { it.y }.average()

        // Sort corners clockwise
        val sortedCorners = corners1.sortedBy {
            val dx = it.x - cx
            val dy = it.y - cy
            atan2(dy, dx)
        }

        // Average elevation for this polygon
        avgElevation /= corners1.size
        avgElevation = clamp(avgElevation)

        val gray = if (cells.isOcean(i) || cells.isCoast(i)) 12
        else (avgElevation * 255).toInt().coerceIn(0, 255)
        g.color = Color(gray, gray, gray)

        val xPoints = sortedCorners.map { it.x.toInt() }.toIntArray()
        val yPoints = sortedCorners.map { it.y.toInt() }.toIntArray()
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
    Biome.ICE to Color(180, 220, 255),
    Biome.LAVA to Color(255, 150, 0)
)
