package amitp.mapgen2.structures

import me.anno.utils.InternalAPI
import me.anno.utils.types.Booleans.toInt
import kotlin.math.max

/**
 * Represents an Array<List<Int>>, with -1 being an invalid value.
 * Is much more memory friendly than Array<List<Int>>.
 *
 * The order of the inserted items may change.
 * */
class PackedIntLists(val size: Int, initialCapacityPerValue: Int) {

    @InternalAPI
    val offsets: IntArray = IntArray(size)

    @InternalAPI
    var values: IntArray

    init {
        val initialCapacityPerValue = max(initialCapacityPerValue, 1)
        val totalCapacity = size * initialCapacityPerValue + (initialCapacityPerValue == 1).toInt()
        values = IntArray(totalCapacity)
        clear()
    }

    fun add(index: Int, value: Int) {
        val pos = offsets[index] + getSize(index)

        // check if next cell is free for end marker
        if (pos + 1 >= values.size) grow()

        if (index + 1 < offsets.size &&
            pos + 1 == offsets[index + 1]
        ) {
            // Need to move suffix forward (shift right until free space)
            offsets[index + 1] = pos + 2
            val wouldBeOverridden = values[pos + 1]
            add(index + 1, wouldBeOverridden)
        }

        // insert value and new end marker
        values[pos] = value
        values[pos + 1] = -1
    }

    fun get(index: Int, index2: Int): Int {
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

    fun getSize(row: Int): Int {
        var pos = offsets[row]
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

    fun clear() {

        // mark values as invalid
        values.fill(-1)

        // distribute blocks evenly
        val initialCapacityPerValue = values.size / size
        for (row in 0 until size) {
            offsets[row] = row * initialCapacityPerValue
        }
    }
}