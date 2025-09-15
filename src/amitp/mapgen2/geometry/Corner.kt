package amitp.mapgen2.geometry

import me.anno.utils.structures.arrays.IntArrayList
import org.joml.Vector2f

class Corner(
    var index: Int,
    val point: Vector2f,
) {

    var border = false
    var water = false
    var ocean = false
    var coast = false

    var elevation = 0f
    var moisture = 0f
    var river = 0

    var downslope: Corner? = null
    var watershed: Corner? = null
    var watershedSize: Int = 0

    val touches = ArrayList<Center>()
    val adjacent = ArrayList<Corner>()
    val protrudes = IntArrayList() // edge indices
}
