package amitp.mapgen2

import amitp.mapgen2.rasterizer.MapRasterizer
import me.anno.image.raw.ByteImage
import me.anno.image.raw.IntImage
import me.anno.utils.Color.r
import me.anno.utils.OS
import org.joml.Vector2f

fun testBiomeRasterizer(map: GeneratedMap): ByteImage {
    val biomes = MapRasterizer.rasterizeBiomes(map, Vector2f(1f))
    val colors = IntImage(biomes.width, biomes.height, false)
    val biomeColors = Biome.entries.map { it.debugColor }.toIntArray()
    biomes.forEachPixel { x, y ->
        colors.setRGB(x, y, biomeColors[biomes.getRGB(x, y).r()])
    }
    colors.write(OS.desktop.getChild("raster-biomes.png"))
    return biomes
}

fun testHeightRasterizer(map: GeneratedMap) {
    MapRasterizer.rasterizeHeight(map, Vector2f(1f))
        .write(OS.desktop.getChild("raster-height.png"))
}

fun testMoistureRasterizer(map: GeneratedMap) {
    MapRasterizer.rasterizeMoisture(map, Vector2f(1f))
        .write(OS.desktop.getChild("raster-moisture.png"))
}
