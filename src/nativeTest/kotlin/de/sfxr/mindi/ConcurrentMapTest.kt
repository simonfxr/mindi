package de.sfxr.mindi

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ConcurrentMapTest {
    @Test
    fun testConcurrentMapCreation() {
        val map = newConcurrentMap<String, Int>()
        assertTrue(map is ConcurrentMap<String, Int>)
        assertEquals(0, map.size)
    }

    @Test
    fun testConcurrentMapOperations() {
        val map = newConcurrentMap<String, Int>()
        map["one"] = 1
        map["two"] = 2
        map["three"] = 3

        assertEquals(3, map.size)
        assertEquals(1, map["one"])
        assertEquals(2, map["two"])
        assertEquals(3, map["three"])

        map.remove("two")
        assertEquals(2, map.size)
        assertEquals(null, map["two"])

        map.clear()
        assertEquals(0, map.size)
    }
}