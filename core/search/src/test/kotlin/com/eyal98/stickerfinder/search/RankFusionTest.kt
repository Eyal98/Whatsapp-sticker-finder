package com.eyal98.stickerfinder.search

import org.junit.Assert.assertEquals
import org.junit.Test

class RankFusionTest {

    @Test
    fun `item ranked well in both lists wins`() {
        val keyword = listOf(1, 2, 3)
        val semantic = listOf(2, 4, 1)
        assertEquals(listOf(2, 1, 4, 3), RankFusion.fuse(listOf(keyword, semantic)))
    }

    @Test
    fun `boost lifts a starred sticker`() {
        val ranked = RankFusion.fuse(listOf(listOf(1, 2)), boosts = mapOf(2 to 0.1))
        assertEquals(listOf(2, 1), ranked)
    }

    @Test
    fun `boost for an id that did not match is ignored`() {
        assertEquals(listOf(1), RankFusion.fuse(listOf(listOf(1)), boosts = mapOf(9 to 1.0)))
    }
}
