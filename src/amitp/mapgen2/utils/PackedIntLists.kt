package amitp.mapgen2.utils

import me.anno.utils.InternalAPI
import me.anno.utils.assertions.assertTrue

/**
 * Represents an Array<List<Int>>, with -1 being an invalid value.
 * Is much more memory friendly than Array<List<Int>>.
 *
 * The order of the inserted items may change.
 * */
class PackedIntLists(val size: Int, avgRowSize: Int) {

    @InternalAPI
    val offsets: IntArray = IntArray(size)

    @InternalAPI
    var values: IntArray

    init {
        assertTrue(avgRowSize > 0)

        val blockSize = avgRowSize * 2
        val totalCapacity = size * blockSize
        values = IntArray(totalCapacity)

        // mark values as invalid
        values.fill(-1)

        // distribute blocks evenly
        for (row in 0 until size) {
            offsets[row] = row * blockSize
        }
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

    inline fun forEach(index: Int, callback: (Int) -> Unit) {
        var pos = offsets[index]
        while (true) {
            val value = values[pos]
            if (value == -1) return
            callback(value)
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
}