package com.eyal98.stickerfinder.search

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.random.Random

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
    fun `packed vectors return most similar first and apply the threshold`() {
        val index = PackedVectors(dims = 2, capacity = 1)
        index.add(1L, floatArrayOf(1f, 0f))
        index.addEncoded(2L, Vectors.encode(floatArrayOf(3f, 3f)))
        index.add(3L, floatArrayOf(-1f, 0f))
        assertEquals(false, index.add(4L, floatArrayOf(1f, 0f, 0f)))
        assertEquals(3, index.size)
        val hits = index.search(Vectors.prepare(floatArrayOf(1f, 0.1f)), limit = 10, minSimilarity = 0.5f)
        assertEquals(listOf(1L, 2L), hits.map { it.first })
    }

    @Test
    fun `packed similarities are within rounding of the float ones`() {
        val random = Random(5)
        fun unit() = Vectors.prepare(FloatArray(768) { random.nextFloat() * 2 - 1 }, 768)
        val vectors = List(500) { unit() }
        val packed = PackedVectors(768)
        vectors.forEachIndexed { i, v -> packed.add(i.toLong(), v) }
        packed.trim()
        repeat(20) {
            val query = unit()
            var worst = 0f
            for (i in vectors.indices) {
                worst = maxOf(worst, abs(packed.dot(i, query) - Vectors.dot(vectors[i], query)))
            }
            assertTrue("off by $worst", worst < 0.005f)
            // The best match found is the best, or within rounding of it.
            val best = vectors.maxOf { Vectors.dot(it, query) }
            val found = packed.search(query, 1, -1f).single().first.toInt()
            assertEquals(best, Vectors.dot(vectors[found], query), 0.005f)
        }
        assertEquals(Vectors.dot(vectors[0], vectors[1]), packed.dot(0, 1), 0.005f)
        assertArrayEquals(vectors[2], packed.vector(2), 0.005f)
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
