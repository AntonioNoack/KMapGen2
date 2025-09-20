package amitp.mapgen2

import amitp.mapgen2.decoration.Lava
import amitp.mapgen2.decoration.NoisyEdges
import amitp.mapgen2.decoration.Roads
import amitp.mapgen2.graphbuilder.GraphBuilder
import amitp.mapgen2.graphbuilder.VoronoiGraphBuilder
import amitp.mapgen2.graphbuilder.perfect.HexHexagonGridBuilder
import amitp.mapgen2.islandshapes.PerlinIslandShape
import amitp.mapgen2.pointselector.GridPointSelector
import amitp.mapgen2.pointselector.RandomPointSelector
import amitp.mapgen2.rasterizer.MapRasterizer
import me.anno.engine.OfficialExtensions
import me.anno.image.raw.ByteImage
import me.anno.image.raw.IntImage
import me.anno.utils.Clock
import me.anno.utils.Color.r
import me.anno.utils.OS
import org.joml.Vector2f
import java.io.File
import javax.imageio.ImageIO
import kotlin.random.Random

val home = System.getProperty("user.home")
val desktop = File(home, "Desktop")

/**
 * Generate a map, and then create all kinds of debug outputs.
 * */
fun main() {

    val mapSize = Vector2f(1000f)
    val numCells = 10000
    val variant = 4284L

    val clock = Clock("VisualTest")
    OfficialExtensions.initForTests() // for png export

    val points = if (false) RandomPointSelector
    else if (false) GridPointSelector(0.7f)
    else HexHexagonGridBuilder()

    val map = MapGen2.generate(
        mapSize,
        PerlinIslandShape(8541),
        points as? GraphBuilder
            ?: VoronoiGraphBuilder(points, false),
        numCells, variant
    )
    clock.stop("Generating Map")

    Roads.generateRoads(map)
    clock.stop("Generate Roads")

    Lava.generateLava(map, Random(variant))
    clock.stop("Generate Lava")

    ImageIO.write(renderBiomeMap(map, mapSize), "PNG", File(desktop, "test_map.png"))
    clock.stop("Render BiomeMap")

    ImageIO.write(renderHeightMap(map, mapSize.x.toInt()), "PNG", File(desktop, "test_height.png"))
    clock.stop("Render HeightMap")

    val noisyEdges = NoisyEdges.generateNoisyEdges(map, Random(variant))
    clock.stop("Generate NoisyEdges")
    ImageIO.write(renderNoisyEdges(noisyEdges, mapSize), "PNG", File(desktop, "test_edges.png"))
    clock.stop("Render NoisyEdges")

    generateObjFileFromCorners(map, mapSize)
    clock.stop("Create OBJ-File")

    val biomes = testBiomeRasterizer(map)
    clock.stop("Rasterize Biomes")

    testHeightRasterizer(map)
    clock.stop("Rasterize Height")

    testMoistureRasterizer(map)
    clock.stop("Rasterize Moisture")

    testRasterizeRivers(map, biomes)
    clock.stop("Rasterize Rivers/Roads/Lava")

    testRasterizeShoreDistance(biomes)
    clock.stop("Rasterize ShoreDistance")

    clock.total()

}

fun testRasterizeRivers(map: GeneratedMap, biomes: ByteImage) {
    MapRasterizer.rasterizeRiversOntoBiomes(map, Vector2f(1f), 3f, biomes)
    MapRasterizer.rasterizeLavaOntoBiomes(map, Vector2f(1f), 2f, biomes)
    MapRasterizer.rasterizeRoadsOntoBiomes(map, Vector2f(1f), 1.5f, biomes)

    val colors = IntImage(biomes.width, biomes.height, false)
    val biomeColors = Biome.entries.map { it.debugColor }.toIntArray()
    biomes.forEachPixel { x, y ->
        colors.setRGB(x, y, biomeColors[biomes.getRGB(x, y).r()])
    }
    colors.write(OS.desktop.getChild("raster-biomes2.png"))
}

fun testRasterizeShoreDistance(biomes: ByteImage) {
    val data = MapRasterizer.rasterizeShoreDistance(biomes)
    val colors = IntImage(biomes.width, biomes.height, false)
    colors.forEachPixel { x, y -> colors.setRGB(x, y, data.data[data.getIndex(x, y)].toInt().and(255) * 0x10101) }
    colors.write(OS.desktop.getChild("raster-shoreDist.png"))
}
