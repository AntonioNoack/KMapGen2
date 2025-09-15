package amitp.mapgen2.geometry

import amitp.mapgen2.Biome
import me.anno.utils.structures.arrays.IntArrayList
import org.joml.Vector2f

class Center(val point: Vector2f) {

    var elevation = 0f
    var moisture = 0f
    var water = false
    var ocean = false
    var coast = false
    var border = false
    var biome: Biome = Biome.OCEAN

    // todo how can we unity these into a struct of arrays?
    //  we could use offset maps...

    val neighbors = ArrayList<Center>()
    val corners = ArrayList<Corner>()
    val borders = IntArrayList() // edge indices
}
