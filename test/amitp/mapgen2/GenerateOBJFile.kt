package amitp.mapgen2

import me.anno.maths.Maths.pow
import me.anno.utils.Color.b01
import me.anno.utils.Color.g01
import me.anno.utils.Color.r01
import java.io.File

fun generateObjFileFromCorners(map: GeneratedMap, mapSize: Float) {
    val corners = map.corners
    val cells = map.cells

    val obj = StringBuilder()
    val mtl = StringBuilder()

    mtl.append("# MTL File\n")
    for (biome in Biome.entries) {
        val color = biome.debugColor
        mtl.append("\nnewmtl ").append(biome.name).append('\n')
            .append("Kd ").append(color.r01()).append(' ')
            .append(color.g01()).append(' ')
            .append(color.b01()).append('\n')
    }

    obj.append("# Wavefront obj\n")
        .append("mtllib map.mtl\n")
    var vi = 1
    val scaleY = mapSize * 0.05f
    var lastBiome: Biome? = null
    for (c in cells.indices) {
        val biome = cells.getBiome(c)
        if (biome != lastBiome) {
            obj.append("usemtl ").append(biome.name).append('\n')
            lastBiome = biome
        }
        val numCorners = cells.corners.forEach(c) { q ->
            obj.append("v ").append(corners.getPointX(q))
                .append(' ').append(pow(corners.getElevation(q), 3f) * scaleY)
                .append(' ').append(corners.getPointY(q))
                .append('\n')
        }
        obj.append("f")
        for (i in 0 until numCorners) {
            obj.append(' ').append(vi + i)
        }
        obj.append('\n')
        vi += numCorners
    }

    File(desktop, "map.mtl").writeText(mtl.toString())
    File(desktop, "map.obj").writeText(obj.toString())
}
