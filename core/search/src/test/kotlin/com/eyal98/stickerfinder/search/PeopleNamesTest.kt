package com.eyal98.stickerfinder.search

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PeopleNamesTest {

    private fun v(vararg x: Float) = Vectors.prepare(x, x.size)

    @Test
    fun `a group gets the name of the saved person it clearly is`() {
        val dana = v(1f, 0f, 0f)
        val noa = v(0f, 1f, 0f)
        val groups = mapOf(10L to v(0.9f, 0.1f, 0f), 11L to v(0.1f, 0.9f, 0.1f), 12L to v(0f, 0f, 1f))
        assertEquals(mapOf(10L to "Dana", 11L to "Noa"), PeopleNames.match(groups, listOf("Dana" to dana, "Noa" to noa)))
    }

    @Test
    fun `two saved people who look alike leave the group unnamed`() {
        val groups = mapOf(1L to v(1f, 1f, 0f))
        assertTrue(PeopleNames.match(groups, listOf("A" to v(1f, 0.95f, 0f), "B" to v(0.95f, 1f, 0f))).isEmpty())
    }

    @Test
    fun `centroid is the unit-length average`() {
        val c = PeopleNames.centroid(listOf(v(1f, 0f), v(0f, 1f)))!!
        assertEquals(0.7071f, c[0], 1e-3f)
        assertEquals(0.7071f, c[1], 1e-3f)
    }
}
