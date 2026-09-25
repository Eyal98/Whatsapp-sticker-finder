package com.eyal98.stickerfinder.search

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test

class VectorsTest {

    @Test
    fun `prepare truncates and normalizes`() {
        val v = Vectors.prepare(floatArrayOf(3f, 4f, 100f), dims = 2)
        assertArrayEquals(floatArrayOf(0.6f, 0.8f), v, 1e-6f)
    }

    @Test
    fun `zero vector stays zero instead of dividing by zero`() {
        assertArrayEquals(floatArrayOf(0f, 0f), Vectors.prepare(floatArrayOf(0f, 0f)), 0f)
    }

    @Test
    fun `encode and decode round trip`() {
        val v = floatArrayOf(0.25f, -1.5f, 3.75f)
        assertArrayEquals(v, Vectors.decode(Vectors.encode(v)), 0f)
    }

    @Test
    fun `index returns most similar first and applies the threshold`() {
        val index = VectorIndex(
            listOf(
                1L to Vectors.prepare(floatArrayOf(1f, 0f)),
                2L to Vectors.prepare(floatArrayOf(1f, 1f)),
                3L to Vectors.prepare(floatArrayOf(-1f, 0f)),
            ),
        )
        val hits = index.search(Vectors.prepare(floatArrayOf(1f, 0.1f)), limit = 10, minSimilarity = 0.5f)
        assertEquals(listOf(1L, 2L), hits.map { it.first })
    }

    @Test
    fun `document text labels fields and skips blanks`() {
        val text = EmbeddingText.document("A sad cat", " ", "cat, sad", "סליחה", "mine")
        assertEquals("A sad cat\nText: סליחה\nTags: cat, sad, mine", text)
        assertNull(EmbeddingText.document(null, "", null, "  ", null))
    }

    @Test
    fun `fingerprint changes with the text`() {
        assertEquals(EmbeddingText.fingerprint("abc"), EmbeddingText.fingerprint("abc"))
        assertNotEquals(EmbeddingText.fingerprint("abc"), EmbeddingText.fingerprint("abd"))
    }
}
