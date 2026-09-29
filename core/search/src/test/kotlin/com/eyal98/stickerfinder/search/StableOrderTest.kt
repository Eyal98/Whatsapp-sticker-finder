package com.eyal98.stickerfinder.search

import org.junit.Assert.assertEquals
import org.junit.Test

class StableOrderTest {

    @Test
    fun `shown items keep their place, gone ones leave, new ones go last`() {
        val shown = listOf("a", "b", "c", "d")
        val next = listOf("e", "d", "b", "a", "f") // "c" gone, "e" and "f" new, the rest reordered
        assertEquals(listOf("a", "b", "d", "e", "f"), StableOrder.merge(shown, next) { it })
    }

    @Test
    fun `kept items take their new version`() {
        data class S(val id: Int, val starred: Boolean)
        val merged = StableOrder.merge(listOf(S(1, false), S(2, false)), listOf(S(2, true), S(1, false))) { it.id }
        assertEquals(listOf(S(1, false), S(2, true)), merged)
    }
}
