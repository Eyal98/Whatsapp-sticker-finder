package com.eyal98.stickerfinder.search

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EvaluationTest {

    @Test
    fun `detects query language`() {
        assertEquals(QueryLanguage.HEBREW, QueryLanguage.detect("חתול עצוב"))
        assertEquals(QueryLanguage.ENGLISH, QueryLanguage.detect("sad cat"))
        assertEquals(QueryLanguage.MIXED, QueryLanguage.detect("חתול sad"))
        assertEquals(QueryLanguage.MIXED, QueryLanguage.detect("😂"))
    }

    @Test
    fun `recall counts hits in the top k out of what fits`() {
        val ranked = listOf("a", "x", "b", "y", "z", "c")
        assertEquals(2.0 / 3, Metrics.recallAt(ranked, setOf("a", "b", "c"), 5), 1e-9)
        val many = (1..8).map { "r$it" }
        assertEquals(1.0, Metrics.recallAt(many, many.toSet(), 5), 1e-9)
        assertEquals(0.0, Metrics.recallAt(ranked, emptySet(), 5), 1e-9)
    }

    @Test
    fun `reciprocal rank uses the first hit within k`() {
        assertEquals(1.0 / 3, Metrics.reciprocalRank(listOf("x", "y", "a"), setOf("a"), 10), 1e-9)
        assertEquals(0.0, Metrics.reciprocalRank(listOf("x", "y", "a"), setOf("a"), 2), 1e-9)
    }

    @Test
    fun `scores are grouped by language with an overall entry`() {
        val he = GoldenQuery("1", "חתול", setOf("a"))
        val en = GoldenQuery("2", "cat", setOf("a"))
        val scores = Metrics.score(listOf(he to listOf("a"), en to listOf("x", "a")))
        assertEquals(1.0, scores[QueryLanguage.HEBREW]!!.recallAt5, 1e-9)
        assertEquals(0.5, scores[QueryLanguage.ENGLISH]!!.mrrAt10, 1e-9)
        assertEquals(0.75, scores[null]!!.mrrAt10, 1e-9)
        assertEquals(2, scores[null]!!.count)
    }

    @Test
    fun `targets need overall recall and a small Hebrew gap`() {
        fun s(r: Double) = Score(r, r, 10)
        assertTrue(EvaluationTargets.met(mapOf(null to s(0.85), QueryLanguage.HEBREW to s(0.82), QueryLanguage.ENGLISH to s(0.86))))
        assertFalse(EvaluationTargets.met(mapOf(null to s(0.85), QueryLanguage.HEBREW to s(0.75), QueryLanguage.ENGLISH to s(0.95))))
        assertFalse(EvaluationTargets.met(mapOf(null to s(0.70))))
    }

    @Test
    fun `percentile picks from sorted values`() {
        assertEquals(30L, Metrics.percentile(listOf(50, 10, 30, 20, 40), 0.5))
        assertEquals(40L, Metrics.percentile(listOf(50, 10, 30, 20, 40), 0.95))
        assertEquals(0L, Metrics.percentile(emptyList(), 0.5))
    }
}
