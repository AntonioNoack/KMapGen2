package amitp.mapgen2.shape

import me.anno.maths.Maths.PIf
import me.anno.maths.Maths.TAUf
import kotlin.math.*
import kotlin.random.Random

class RadialIslandShape(
    rnd: Random,

    /**
     * 1.0 means no small islands 2.0 leads to a lot
     * */
    val islandFactor: Float = 1.07f
) : IslandShape {
    constructor(seed: Long, islandFactor: Float) : this(Random(seed), islandFactor)

    val bumps = rnd.nextInt(1, 6)
    val startAngle = rnd.nextFloat() * TAUf
    val dipAngle = rnd.nextFloat() * TAUf
    val dipWidth = rnd.nextFloat() * 0.5f + 0.2f

    override fun isOnLand(x: Float, y: Float): Boolean {
        val angle = atan2(y, x)
        val length = 0.5 * (max(abs(x), abs(y)) + hypot(x, y))

        var r1 = 0.5 + 0.40 * sin(startAngle + bumps * angle + cos((bumps + 3) * angle))
        var r2 = 0.7 - 0.20 * sin(startAngle + bumps * angle - sin((bumps + 2) * angle))
        if (abs(angle - dipAngle) < dipWidth
            || abs(angle - dipAngle + 2 * PIf) < dipWidth
            || abs(angle - dipAngle - 2 * PIf) < dipWidth
        ) {
            r1 = 0.2
            r2 = 0.2
        }
        return length < r1 || (length > r1 * islandFactor && length < r2)
    }
}