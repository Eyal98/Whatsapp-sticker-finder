package com.eyal98.stickerfinder.search

import org.junit.Assert.assertEquals
import org.junit.Test

class TextNormalizerTest {

    @Test
    fun `strips niqqud`() {
        assertEquals(TextNormalizer.normalize("שלום"), TextNormalizer.normalize("שָׁלוֹם"))
    }

    @Test
    fun `maps final letters to regular forms`() {
        assertEquals("שלומ", TextNormalizer.normalize("שלום"))
        assertEquals("כלב", TextNormalizer.normalize("כלב"))
        assertEquals("ארצ", TextNormalizer.normalize("ארץ"))
    }

    @Test
    fun `removes geresh and gershayim inside words`() {
        val expected = "מזלט"
        assertEquals(expected, TextNormalizer.normalize("מזל\"ט"))
        assertEquals(expected, TextNormalizer.normalize("מזל״ט"))
    }

    @Test
    fun `maqaf separates words`() {
        assertEquals(listOf("בית", "ספר"), TextNormalizer.tokenize("בית־ספר"))
    }

    @Test
    fun `lowercases and splits mixed-language text`() {
        assertEquals(listOf("sad", "חתול", "123"), TextNormalizer.tokenize("  SAD, חתול!! 123 "))
    }

    @Test
    fun `collapses long runs of the same letter`() {
        assertEquals("חחח", TextNormalizer.normalize("חחחחחחח"))
        assertEquals("חחח", TextNormalizer.normalize("חחח"))
        assertEquals("sooo", TextNormalizer.normalize("sooooooo"))
    }

    @Test
    fun `empty and punctuation-only input yields no tokens`() {
        assertEquals(emptyList<String>(), TextNormalizer.tokenize(""))
        assertEquals(emptyList<String>(), TextNormalizer.tokenize(" ?! ... "))
    }
}
