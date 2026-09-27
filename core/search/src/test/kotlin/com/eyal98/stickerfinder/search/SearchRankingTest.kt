package com.eyal98.stickerfinder.search

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchRankingTest {

    private fun f(text: String?, weight: Double) = KeywordRelevance.Field(text, weight)

    @Test
    fun `a sticker matching every word beats one matching a filler word`() {
        val terms = QueryParser.parse("running late again")
        val all = KeywordRelevance.score(terms, listOf(f("running late again", KeywordRelevance.SEEN)))
        val one = KeywordRelevance.score(terms, listOf(f("see you again", KeywordRelevance.SEEN)))
        assertTrue("$all vs $one", all > one)
    }

    @Test
    fun `the user's own tag counts more than printed text`() {
        val terms = QueryParser.parse("kermit")
        val tagged = KeywordRelevance.score(terms, listOf(f("Kermit", KeywordRelevance.USER), f(null, KeywordRelevance.SEEN)))
        val printed = KeywordRelevance.score(terms, listOf(f(null, KeywordRelevance.USER), f("kermit", KeywordRelevance.SEEN)))
        assertEquals(1.0, tagged, 1e-9)
        assertTrue(tagged > printed)
    }

    @Test
    fun `the last word matches as a prefix, and Hebrew prefixes are understood`() {
        assertEquals(1.0, KeywordRelevance.score(QueryParser.parse("kerm"), listOf(f("kermit", 3.0))), 1e-9)
        assertEquals(1.0, KeywordRelevance.score(QueryParser.parse("והחתול"), listOf(f("חתול", 3.0))), 1e-9)
    }

    private val day = 24 * 60 * 60 * 1000L

    @Test
    fun `picks for the same search come first, then similar searches`() {
        val now = 100 * day
        val picks = listOf(
            Pick("kermit", 1, count = 3, lastAt = now),
            Pick("kermit the frog", 2, count = 4, lastAt = now),
            Pick("pizza", 3, count = 9, lastAt = now),
        )
        assertEquals(listOf(1L, 2L), PickRanking.rank("Kermit", picks, now))
        // A prefix of the remembered search counts too.
        assertEquals(setOf(1L, 2L), PickRanking.rank("kerm", picks, now).toSet())
        assertEquals(emptyList<Long>(), PickRanking.rank("coffee", picks, now))
    }

    @Test
    fun `old picks fade`() {
        val now = 400 * day
        val picks = listOf(
            Pick("late", 1, count = 4, lastAt = now - 300 * day),
            Pick("late", 2, count = 1, lastAt = now),
        )
        assertEquals(listOf(2L, 1L), PickRanking.rank("late", picks, now))
    }

    @Test
    fun `a weighted ranking counts more`() {
        val fused = RankFusion.fuse(listOf(listOf("a", "b"), listOf("b")), weights = listOf(1.0, 0.01))
        assertEquals(listOf("a", "b"), fused)
        val boosted = RankFusion.fuse(listOf(listOf("a", "b"), listOf("b")), weights = listOf(1.0, 2.0))
        assertEquals(listOf("b", "a"), boosted)
    }
}
