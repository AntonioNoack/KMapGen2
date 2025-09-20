package amitp.mapgen2.structures

import me.anno.utils.assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.random.Random

class PackedIntListsTest {

    @Test
    fun testAddAndGet() {
        val lists = PackedIntLists(size = 3, initialCapacityPerValue = 2)
        lists.add(0, 10)
        lists.add(0, 20)
        lists.add(1, 99)

        assertEquals(10, lists[0, 0])
        assertEquals(20, lists[0, 1])
        assertEquals(99, lists[1, 0])
        assertEquals(2, lists.getSize(0))
        assertEquals(1, lists.getSize(1))
        assertEquals(0, lists.getSize(2))
    }

    @Test
    fun testForEach() {
        val lists = PackedIntLists(size = 1, initialCapacityPerValue = 2)
        lists.add(0, 1)
        lists.add(0, 2)
        lists.add(0, 3)

        val collected = mutableListOf<Int>()
        lists.forEach(0) { collected.add(it) }

        assertEquals(listOf(1, 2, 3), collected)
    }

    @Test
    fun testAppendingWithNecessaryRightShift() {
        val lists = PackedIntLists(size = 2, initialCapacityPerValue = 1)
        // Row 0 and 1 will be packed close together
        println("[[],[]] ${lists.offsets.toList()}, ${lists.values.toList()}")
        lists.add(0, 11)
        println("[[11],[]] ${lists.offsets.toList()}, ${lists.values.toList()}")
        lists.add(0, 12) // should trigger shift for row 1’s sentinel
        println("[[11,12],[]] ${lists.offsets.toList()}, ${lists.values.toList()}")
        lists.add(1, 99)
        println("[[11,12],[99]] ${lists.offsets.toList()}, ${lists.values.toList()}")


        assertEquals(listOf(11, 12), rowToList(lists, 0))
        assertEquals(listOf(99), rowToList(lists, 1))
    }

    @Test
    fun testGrowingWhenOutOfCapacity() {
        val lists = PackedIntLists(size = 1, initialCapacityPerValue = 1)
        // Fill far beyond initial size
        for (i in 0 until 50) {
            lists.add(0, i)
        }
        assertEquals(50, lists.getSize(0))
        assertEquals(42, lists[0, 42])
    }

    @Test
    fun testThrowsOutOfBounds() {
        val lists = PackedIntLists(size = 1, initialCapacityPerValue = 1)
        lists.add(0, 7)
        assertThrows<IndexOutOfBoundsException> {
            lists[0, 1]
        }
    }

    @Test
    fun testRandomizedAddsForConsistency() {
        val rowCount = 20
        val ops = 20_000
        val avgRowSize = 10

        val packed = PackedIntLists(rowCount, avgRowSize)
        val reference = Array(rowCount) { ArrayList<Int>() }

        val rng = Random(12345)

        repeat(ops) { step ->
            val index = rng.nextInt(rowCount)
            val value = step // unique per step so we can track

            packed.add(index, value)
            reference[index].add(value)

            // println("[#$step, [$index]+=$value], state: ${packed.offsets.toList()}, ${packed.values.toList()}")
        }

        // Verify row-by-row consistency
        for (index in 0 until rowCount) {
            val expected = reference[index]
            val actual = rowToList(packed, index).sorted()
            assertEquals(expected, actual, "Mismatch in index=$index")
        }
    }

    private fun rowToList(lists: PackedIntLists, row: Int): List<Int> {
        val result = ArrayList<Int>()
        lists.forEach(row) { result.add(it) }
        return result
    }
}