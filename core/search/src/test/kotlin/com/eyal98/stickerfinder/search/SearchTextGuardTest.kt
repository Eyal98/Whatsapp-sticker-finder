package com.eyal98.stickerfinder.search

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The invariants the FTS layer relies on but never states in a test: [FtsQueryBuilder] builds
 * `MATCH` expressions without escaping anything, which is only safe while every token out of
 * [TextNormalizer] is letters and digits. A token that kept a quote, a colon or an uppercase
 * `OR` would turn a search into a SQLite syntax error on the user's phone.
 *
 * Also the Hebrew and mixed-script cases: a query typed with the phone's RTL marks, niqqud, or
 * final letters has to reach the same index terms the sticker's printed text produced.
 */
class SearchTextGuardTest {

    /** FTS4's own syntax: quotes, the prefix star, grouping, column and NEAR/^ operators. */
    private val FTS_SYNTAX = charArrayOf('"', '\'', '*', '(', ')', ':', '-', '^', '+', ',')

    @Test
    fun `tokens are only letters and digits, so no FTS expression needs escaping`() {
        val typed = """OR AND NOT NEAR "quoted" (group) column:value -minus ^caret a*b 50% @at #hash"""
        val tokens = TextNormalizer.tokenize(typed)
        assertTrue(tokens.isNotEmpty())
        for (token in tokens) {
            assertTrue("$token holds FTS syntax", FTS_SYNTAX.none { it in token })
            assertTrue("$token is not all letters and digits", token.all(Char::isLetterOrDigit))
            // Uppercase is what makes OR/NOT/NEAR operators rather than words.
            assertEquals("$token is not lowercased", token.lowercase(), token)
        }
    }

    @Test
    fun `an FTS operator typed by the user is just a word`() {
        assertEquals(listOf("or"), TextNormalizer.tokenize("OR"))
        assertEquals(listOf("near"), TextNormalizer.tokenize("NEAR"))
        // And it stays a word all the way into the match expression.
        val match = FtsQueryBuilder.matchAll(QueryParser.parse("NOT"))
        assertFalse(match!!.contains("NOT"))
    }

    @Test
    fun `invisible direction marks never become part of a word`() {
        // What an RTL keyboard and a chat app actually insert around Hebrew: RLM, LRM, and the
        // first-strong isolate pair.
        val marked = "⁦‏חתול‎⁩ sad"
        assertEquals(listOf("חתול", "sad"), TextNormalizer.tokenize(marked))
    }

    @Test
    fun `invisible formatting never lands inside a token or makes an empty one`() {
        // A zero-width joiner, a soft hyphen and a byte-order mark all separate rather than being
        // kept; what matters for FTS is that no token is empty and none carries the character.
        val invisible = listOf('‍', '­', '﻿', '​')
        for (c in invisible) {
            val tokens = TextNormalizer.tokenize("ab${c}cd")
            assertTrue("$tokens from U+%04X".format(c.code), tokens.all { it.isNotEmpty() && c !in it })
            assertEquals("ab", tokens.first())
        }
    }

    @Test
    fun `a Hebrew query reaches the terms the sticker's printed text produced`() {
        // The sticker's OCR text, as stored in the FTS table.
        val indexed = IndexTerms.build("שָׁלוֹם חברים").split(" ")
        // The same word typed without niqqud, with its final letter, on a phone that adds an RLM.
        val term = QueryParser.parse("‏שלום").single()
        assertTrue(
            "none of ${term.alternatives} is in $indexed",
            term.alternatives.any { it in indexed },
        )
    }

    @Test
    fun `a mixed Hebrew and English query keeps both words`() {
        val terms = QueryParser.parse("חתול sad")
        assertEquals(listOf("חתול", "sad"), terms.map { it.original })
        // Only the last word is matched as a prefix, whichever script it is in.
        val match = FtsQueryBuilder.matchAll(terms)!!
        assertTrue(match, "sad*" in match)
        assertFalse(match, "חתול*" in match)
    }

    @Test
    fun `a word typed with its final letter matches the same term either way`() {
        val withFinal = QueryParser.parse("שלום").single().alternatives
        val withRegular = QueryParser.parse("שלומ").single().alternatives
        assertEquals(withRegular, withFinal)
    }

    @Test
    fun `every alternative is a single term, so the match expression stays well formed`() {
        // An alternative holding a space would silently become two ANDed terms inside an OR group.
        for (query in listOf("חתול עצוב", "cannot", "מזל״ט", "שלום", "a cat that is sad")) {
            for (term in QueryParser.parse(query)) {
                assertTrue("empty alternative set for $query", term.alternatives.isNotEmpty())
                for (alt in term.alternatives) {
                    assertTrue("'$alt' is not one term", alt.isNotBlank() && ' ' !in alt)
                }
            }
        }
    }

    @Test
    fun `a query of only invisible marks builds no expression`() {
        assertTrue(QueryParser.parse("‏‎⁦⁩").isEmpty())
        assertEquals(null, FtsQueryBuilder.matchAll(QueryParser.parse("‏‎")))
        assertEquals(null, FtsQueryBuilder.matchAny(QueryParser.parse("‏‎")))
    }

    @Test
    fun `a very long pasted query still produces usable terms`() {
        // Pasting a chat line into the search box must not produce an empty or malformed match.
        val pasted = "חתול עצוב ".repeat(50) + "sad cat ".repeat(50)
        val match = FtsQueryBuilder.matchAll(QueryParser.parse(pasted))!!
        assertTrue(match.isNotEmpty())
        assertFalse("\"\"" in match)
    }
}
