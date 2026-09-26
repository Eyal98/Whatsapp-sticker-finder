package com.eyal98.stickerfinder.search

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.sqrt

class FaceGroupingTest {

    /** A unit vector near axis [axis], nudged by [noise] along axis [noiseAxis]. */
    private fun v(axis: Int, noise: Float = 0f, noiseAxis: Int = 7): FloatArray {
        val a = FloatArray(8)
        a[axis] = 1f
        a[noiseAxis] += noise
        val n = sqrt(a.sumOf { (it * it).toDouble() }).toFloat()
        return FloatArray(8) { a[it] / n }
    }

    @Test
    fun `new faces join the existing group they match`() {
        val result = FaceGrouping.group(
            groups = mapOf(10L to listOf(v(0), v(0, 0.1f))),
            ungrouped = listOf(FaceGrouping.Face(1, v(0, 0.2f)), FaceGrouping.Face(2, v(3))),
        )
        assertEquals(mapOf(1L to 10L), result.joined)
        assertTrue(result.newGroups.isEmpty())
    }

    @Test
    fun `ungrouped faces form new groups of the same person only`() {
        val faces = listOf(
            FaceGrouping.Face(1, v(1)),
            FaceGrouping.Face(2, v(2)),
            FaceGrouping.Face(3, v(1, 0.2f)),
            FaceGrouping.Face(4, v(2, 0.1f)),
            FaceGrouping.Face(5, v(4)),
        )
        val groups = FaceGrouping.group(emptyMap(), faces).newGroups.map { it.toSet() }.toSet()
        // Face 5 matches nobody, so it stays ungrouped.
        assertEquals(setOf(setOf(1L, 3L), setOf(2L, 4L)), groups)
    }
}
