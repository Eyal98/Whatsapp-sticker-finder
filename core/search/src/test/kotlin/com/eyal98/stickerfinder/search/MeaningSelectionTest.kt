package com.eyal98.stickerfinder.search

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [MeaningSelection]'s cut-off and [MeaningIndex]'s compact storage at the shapes a real library
 * produces: more vectors than the builder first allocates, a query from a model since replaced,
 * and libraries full of forwarded copies of the same sticker.
 */
class MeaningSelectionTest {

    private fun scored(vararg sims: Float, pack: (Int) -> Int = { -1 }) =
        sims.mapIndexed { i, s -> MeaningSelection.Scored(i.toLong(), s, pack(i)) }

    private fun unit(dims: Int, axis: Int = 0) =
        Vectors.prepare(FloatArray(dims) { if (it == axis) 1f else 0f }, dims)

    // --- The cut-off's boundaries -------------------------------------------------------------

    @Test
    fun `below thirty stickers only the minimum similarity applies`() {
        // 28 unrelated stickers plus one real match: too few for the spread to mean anything, so
        // everything above the user's minimum is kept rather than guessed at.
        val noise = FloatArray(28) { 0.28f + (it % 5) * 0.01f }
        val hits = MeaningSelection.select(scored(*noise, 0.5f), minSimilarity = 0.15f, limit = 50)
        assertEquals(29, hits.size)
    }

    @Test
    fun `from thirty stickers the spread filters the unrelated ones`() {
        // The same library with one more sticker: now the z-score applies and only the match survives.
        val noise = FloatArray(29) { 0.28f + (it % 5) * 0.01f }
        val hits = MeaningSelection.select(scored(*noise, 0.5f), minSimilarity = 0.15f, limit = 50)
        assertEquals(listOf(29L), hits.map { it.first })
    }

    @Test
    fun `results come back best first`() {
        val noise = FloatArray(100) { 0.20f + (it % 5) * 0.01f }
        val hits = MeaningSelection.select(scored(*noise, 0.55f, 0.80f, 0.65f), minSimilarity = 0.15f, limit = 10)
        assertEquals(listOf(101L, 102L, 100L), hits.map { it.first })
        assertTrue(hits.zipWithNext().all { (a, b) -> a.second >= b.second })
    }

    @Test
    fun `stickers outside a pack are never capped`() {
        // Loose stickers (pack -1) must not share the per-pack budget with one another.
        val noise = FloatArray(100) { 0.20f + (it % 3) * 0.01f }
        val matches = FloatArray(20) { 0.70f - it * 0.001f }
        val hits = MeaningSelection.select(scored(*(noise + matches)), minSimilarity = 0.15f, limit = 50)
        assertEquals(20, hits.size)
    }

    @Test
    fun `an empty library finds nothing`() {
        assertTrue(MeaningSelection.select(emptyList(), minSimilarity = 0.15f, limit = 10).isEmpty())
    }

    // --- Scores with no spread ----------------------------------------------------------------
    // A median absolute deviation of zero used to make `spread` zero, so the cut-off fell back to
    // the median itself and every sticker sitting at the median passed `>= cutOff` (PEE-7). The
    // spread is floored now; these guard against that regression.

    @Test
    fun `a search every sticker answers equally finds nothing`() {
        val flat = FloatArray(200) { 0.30f }
        assertTrue(MeaningSelection.select(scored(*flat), minSimilarity = 0.15f, limit = 100).isEmpty())
    }

    @Test
    fun `copies of one sticker don't answer every search`() {
        // A WhatsApp library is full of the same sticker forwarded many times. Identical pictures
        // embed identically, so 60 copies all score the same against any query; they must not
        // become that query's matches just because they agree with each other.
        val varied = FloatArray(40) { 0.20f + (it % 10) * 0.01f }
        val copies = FloatArray(60) { 0.30f }
        val hits = MeaningSelection.select(scored(*(varied + copies)), minSimilarity = 0.15f, limit = 50)
        assertTrue("got ${hits.size} matches for a query nothing is close to", hits.isEmpty())
    }

    @Test
    fun `a real match is still found in a library the copies dominate`() {
        // The same library as above with one sticker the search is actually about. The copies still
        // flatten the spread to zero, so the floor has to leave a standout findable: "nothing
        // discriminates" must mean a higher bar, not an empty answer.
        val varied = FloatArray(40) { 0.20f + (it % 10) * 0.01f }
        val copies = FloatArray(60) { 0.30f }
        val hits = MeaningSelection.select(scored(*(varied + copies), 0.72f), minSimilarity = 0.15f, limit = 50)
        assertEquals(listOf(100L), hits.map { it.first })
    }

    // --- The compact index --------------------------------------------------------------------

    @Test
    fun `the compact index keeps every vector past its first allocation`() {
        // The builder starts at 256 rows and doubles; the grown copy must carry the codes with it.
        val dims = 8
        val rows = 300
        val builder = MeaningIndex.Builder(dims)
        repeat(rows) { i -> builder.add(i.toLong(), unit(dims, axis = i % dims), pack = -1) }
        val index = builder.build()
        assertEquals(rows, index.size)
        // Every sticker still scores, and the ones on the query's axis score 1 — including the
        // ones written after the arrays were grown and copied.
        val scores = index.scores(unit(dims, axis = 0))
        assertEquals(rows, scores.size)
        assertEquals((0 until rows).count { it % dims == 0 }, scores.count { it.similarity > 0.99f })
        // Nothing off the axis picked up a neighbour's codes.
        assertTrue(scores.none { it.similarity > 0.01f && it.similarity < 0.99f })
    }

    @Test
    fun `a vector of another size is left out instead of corrupting the index`() {
        val builder = MeaningIndex.Builder(4)
        builder.add(1L, unit(4), pack = -1)
        builder.add(2L, unit(8), pack = -1)
        assertEquals(1, builder.build().size)
    }

    @Test
    fun `a query from another model finds nothing rather than scoring on part of it`() {
        val index = MeaningIndex.Builder(4).apply { add(1L, unit(4), pack = -1) }.build()
        assertTrue(index.scores(unit(8)).isEmpty())
        assertTrue(index.scores(FloatArray(0)).isEmpty())
    }

    @Test
    fun `a sticker with no usable vector scores zero rather than failing`() {
        // A zero vector survives `prepare` (it can't be scaled), so it must still score.
        val index = MeaningIndex.Builder(4).apply { add(1L, FloatArray(4), pack = -1) }.build()
        assertEquals(0f, index.scores(unit(4)).single().similarity, 0f)
    }
}
