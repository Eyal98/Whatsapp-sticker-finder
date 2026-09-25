package com.eyal98.stickerfinder.search

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchTermsTest {

    @Test
    fun `index terms include prefix variants and no duplicates`() {
        val terms = IndexTerms.build("והחתול", "חתול", null).split(" ")
        assertEquals(listOf("והחתול", "החתול", "חתול"), terms)
    }

    @Test
    fun `query terms expand to synonyms in the other language`() {
        val term = QueryParser.parse("חתולים עצוב").last()
        assertTrue("sad" in term.alternatives)
        assertTrue("עצוב" in term.alternatives)
    }

    @Test
    fun `prefixed query word finds synonyms of its stem`() {
        val term = QueryParser.parse("והחתול").single()
        assertTrue("cat" in term.alternatives)
    }

    @Test
    fun `match all joins words with implicit AND and only the last word is a prefix`() {
        val terms = listOf(
            QueryTerm("a", linkedSetOf("a", "b")),
            QueryTerm("c", linkedSetOf("c", "d")),
        )
        assertEquals("a OR b c* OR d", FtsQueryBuilder.matchAll(terms))
        assertEquals("a OR b OR c* OR d", FtsQueryBuilder.matchAny(terms))
    }

    @Test
    fun `empty query builds no expression`() {
        assertNull(FtsQueryBuilder.matchAll(QueryParser.parse("  !? ")))
    }
}
