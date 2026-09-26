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

    private fun face(id: Long, vector: FloatArray) = FaceGrouping.Face(id, vector)

    @Test
    fun `new faces join the group of their closest grouped face`() {
        val result = FaceGrouping.group(
            grouped = listOf(face(100, v(0)) to 10L, face(101, v(1)) to 11L),
            ungrouped = listOf(face(1, v(0, 0.2f)), face(2, v(3))),
        )
        assertEquals(mapOf(1L to 10L), result.joined)
        assertTrue(result.newGroups.isEmpty())
    }

    @Test
    fun `ungrouped faces form new groups of the same person only`() {
        val faces = listOf(face(1, v(1)), face(2, v(2)), face(3, v(1, 0.2f)), face(4, v(2, 0.1f)), face(5, v(4)))
        val groups = FaceGrouping.group(emptyList(), faces).newGroups.map { it.toSet() }.toSet()
        // Face 5 matches nobody, so it stays ungrouped.
        assertEquals(setOf(setOf(1L, 3L), setOf(2L, 4L)), groups)
    }

    @Test
    fun `many different people don't collapse into one group`() {
        // Six people, three faces each, all different directions: an average of all of them is
        // close to none, and no face is close to another person's.
        val faces = (0 until 6).flatMap { p -> (0 until 3).map { k -> face(p * 10L + k, v(p, 0.1f * k, noiseAxis = 6 + (k % 2))) } }
        val groups = FaceGrouping.group(emptyList(), faces).newGroups
        assertEquals(6, groups.size)
        assertTrue(groups.all { g -> g.map { it / 10 }.toSet().size == 1 })
    }
}
