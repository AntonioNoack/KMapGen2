package amitp.mapgen2.structures

import me.anno.maths.Maths.ceilDiv
import me.anno.utils.InternalAPI
import me.anno.utils.types.Booleans.toInt
import kotlin.math.max

/**
 * Represents an Array<List<Int>>, with -1 being an invalid value.
 * Is much more memory friendly than Array<List<Int>>.
 *
 * Be wary that if you underestimate initialCapacityPerValue, this collect gets really slow!
 * The order of the inserted items may change.
 * */
class PackedIntLists(val size: Int, initialCapacityPerValue: Int) {

    @InternalAPI
    var offsets: IntArray = IntArray(size)

    @InternalAPI
    var values: IntArray

    init {
        val initialCapacityPerValue = max(initialCapacityPerValue, 1)
        val totalCapacity = size * initialCapacityPerValue + (initialCapacityPerValue == 1).toInt()
        values = IntArray(totalCapacity)
        clear()
    }

    fun addUnique(index: Int, value: Int) {
        if (value < 0 || contains(index, value)) return
        add(index, value)
    }

    fun add(index: Int, value: Int) {
        var index = index
        var value = value
        while (true) {
            val pos = offsets[index] + getSize(index)

            // check if next cell is free for end marker
            if (pos + 1 >= values.size) grow()

            val wouldBeOverridden = values[pos + 1]

            // insert value and new end marker
            values[pos] = value
            values[pos + 1] = -1

            index++
            if (index < offsets.size && pos + 1 == offsets[index]) {
                // Need to move suffix forward (shift right until free space)
                offsets[index] = pos + 2
                value = wouldBeOverridden
            } else break
        }
    }

    operator fun get(index: Int, index2: Int): Int {
        var pos = offsets[index]
        var count = 0
        while (values[pos] != -1) {
            if (count == index2) return values[pos]
            pos++
            count++
        }
        throw IndexOutOfBoundsException("row=$index col=$index2")
    }

    inline fun forEach(index: Int, callback: (Int) -> Unit): Int {
        val pos0 = offsets[index]
        var pos = pos0
        while (true) {
            val value = values[pos]
            if (value == -1) return pos - pos0
            callback(value)
            pos++
        }
    }

    fun contains(index: Int, value: Int): Boolean {
        var pos = offsets[index]
        while (true) {
            val valueI = values[pos]
            if (valueI == -1) return false
            if (valueI == value) return true
            pos++
        }
    }

    fun getSize(index: Int): Int {
        var pos = offsets[index]
        var count = 0
        while (values[pos] != -1) {
            count++
            pos++
        }
        return count
    }

    private fun grow() {
        val values = values
        val newValues = values.copyOf(values.size * 2)
        newValues.fill(-1, values.size, newValues.size)
        this.values = newValues
    }

    /**
     * Clears all values for all indices
     * */
    fun clear() {

        // mark values as invalid
        values.fill(-1)

        // distribute blocks evenly
        val factor = values.size.toLong().shl(32) / max(size, 1)
        for (row in 0 until size) {
            offsets[row] = (row * factor).shr(32).toInt()
        }
    }

    /**
     * Clears all values for this index
     * */
    fun clear(index: Int) {
        val size = getSize(index)
        val offset = offsets[index]
        for (i in 0 until size) {
            values[offset + i] = -1
        }
    }

    fun sortBy(index: Int, getAngle: GetFloat) {
        val size = getSize(index)
        val offset = offsets[index]
        for (i in offset + 1 until offset + size) {
            for (j in offset until i) { // j<i
                val vi = values[i]
                val vj = values[j]
                if (getAngle.map(vj) > getAngle.map(vi)) { // swap
                    values[j] = vi
                    values[i] = vj
                }
            }
        }
    }

    fun resizeTo(newSize: Int) {
        val cellsPerSize = ceilDiv(values.size, size)
        val lastCell = if (size == 0) 0 else offsets[size - 1] + getSize(size - 1)
        val requiredSize = lastCell + (newSize - size + 1) * cellsPerSize

        val oldSize = values.size
        if (requiredSize > oldSize) {
            values = values.copyOf(requiredSize)
            values.fill(-1, oldSize, requiredSize)
        }
        offsets = offsets.copyOf(newSize)
    }
}