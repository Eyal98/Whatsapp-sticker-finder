package com.eyal98.stickerfinder.search

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
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
    fun `compact index scores like floats and takes each sticker's best vector`() {
        val a = Vectors.prepare(floatArrayOf(1f, 0f, 0f))
        val b = Vectors.prepare(floatArrayOf(0.6f, 0.8f, 0f))
        val c = Vectors.prepare(floatArrayOf(0f, 0f, 1f))
        val index = MeaningIndex.Builder(3).apply {
            add(1L, a, pack = -1)
            add(1L, c, pack = -1) // sticker 1's second vector
            add(2L, b, pack = -1)
        }.build()
        val query = Vectors.prepare(floatArrayOf(0.1f, 0.2f, 1f))
        val scores = index.scores(query).associate { it.stickerId to it.similarity }
        assertEquals(Vectors.dot(query, c), scores.getValue(1L), 0.01f)
        assertEquals(Vectors.dot(query, b), scores.getValue(2L), 0.01f)
        assertEquals(3, index.size)
    }

    private fun scored(vararg sims: Float, pack: (Int) -> Int = { -1 }) =
        sims.mapIndexed { i, s -> MeaningSelection.Scored(i.toLong(), s, pack(i)) }

    @Test
    fun `only stickers that stand out from the rest are matches`() {
        // 100 unrelated stickers around 0.30, two real matches.
        val noise = FloatArray(100) { 0.28f + (it % 5) * 0.01f }
        val scores = scored(*noise, 0.62f, 0.55f)
        val hits = MeaningSelection.select(scores, minSimilarity = 0.15f, limit = 50)
        assertEquals(listOf(100L, 101L), hits.map { it.first })
    }

    @Test
    fun `a vague search finds nothing rather than a hundred weak matches`() {
        val flat = FloatArray(200) { 0.30f + (it % 10) * 0.005f }
        assertTrue(MeaningSelection.select(scored(*flat), minSimilarity = 0.15f, limit = 100).isEmpty())
    }

    @Test
    fun `a big group of real matches doesn't hide itself`() {
        // 60 cat stickers at 0.6 among 400 others: the median-based spread isn't inflated by them.
        val sims = FloatArray(400) { 0.25f + (it % 7) * 0.01f } + FloatArray(60) { 0.6f }
        assertEquals(60, MeaningSelection.select(scored(*sims), minSimilarity = 0.15f, limit = 100).size)
    }

    @Test
    fun `one pack can't fill the list`() {
        val sims = FloatArray(100) { 0.2f + (it % 3) * 0.01f } + FloatArray(10) { 0.7f - it * 0.01f }
        val hits = MeaningSelection.select(scored(*sims) { i -> if (i >= 100) 7 else -1 }, minSimilarity = 0.15f, limit = 50)
        assertEquals(MeaningSelection.MAX_PER_PACK, hits.size)
    }

    @Test
    fun `the user's minimum similarity still applies`() {
        val noise = FloatArray(100) { 0.05f + (it % 5) * 0.01f }
        assertTrue(MeaningSelection.select(scored(*noise, 0.3f), minSimilarity = 0.4f, limit = 10).isEmpty())
    }

    @Test
    fun `printed text and what the sticker shows are separate, and the pack name is left out`() {
        val facets = EmbeddingText.facets(
            captionEn = null, captionHe = null, captionTags = null,
            ocrText = "running late", userTags = "Kermit", imageTags = "frog, green",
            emojiWords = "laughing", peopleNames = null, userDescription = null,
        )
        assertEquals("running late", facets[Facet.SAYS])
        assertEquals("Kermit, frog, green, laughing", facets[Facet.SHOWS])
        // OCR noise isn't embedded as words.
        val noisy = EmbeddingText.facets(null, null, null, "l|, .", null, "cat", null, null, null)
        assertNull(noisy[Facet.SAYS])
        assertEquals(setOf(Facet.SHOWS), noisy.keys)
    }

    @Test
    fun `fingerprint changes with the text`() {
        assertEquals(EmbeddingText.fingerprint("abc"), EmbeddingText.fingerprint("abc"))
        assertNotEquals(EmbeddingText.fingerprint("abc"), EmbeddingText.fingerprint("abd"))
    }
}
