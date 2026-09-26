package com.eyal98.stickerfinder.search

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class LearnedTagsTest {

    private val dims = 64
    private val rng = Random(1)

    /** A random direction: unrelated stickers. */
    private fun randomVector() = FloatArray(dims) { rng.nextFloat() * 2 - 1 }

    /** A point near [center]: a sticker of the same character. */
    private fun near(center: FloatArray, noise: Float) = FloatArray(dims) { center[it] + (rng.nextFloat() * 2 - 1) * noise }

    private fun world(): Triple<FloatArray, MutableMap<Long, FloatArray>, MutableMap<Long, List<String>>> {
        val kermit = randomVector()
        val vectors = HashMap<Long, FloatArray>()
        val tags = HashMap<Long, List<String>>()
        // 200 unrelated stickers.
        for (id in 1L..200L) vectors[id] = randomVector()
        // 10 Kermit stickers; the user tagged 4 of them.
        for (id in 1001L..1010L) vectors[id] = near(kermit, 0.2f)
        for (id in 1001L..1004L) tags[id] = listOf("Kermit")
        return Triple(kermit, vectors, tags)
    }

    @Test
    fun `suggests a tag on the untagged look-alikes only`() {
        val (_, vectors, tags) = world()
        val result = LearnedTags.suggest(vectors, tags)
        val tagged = result.suggestions.filterValues { list -> list.any { it.tag == "Kermit" } }.keys
        assertEquals((1005L..1010L).toSet(), tagged)
        assertEquals(1, result.stats.learnedTags)
    }

    @Test
    fun `a hidden suggestion stays hidden`() {
        val (_, vectors, tags) = world()
        val result = LearnedTags.suggest(vectors, tags, blocked = mapOf(1005L to setOf("kermit")))
        assertFalse(result.suggestions[1005L].orEmpty().any { it.tag == "Kermit" })
        assertTrue(result.suggestions[1006L].orEmpty().any { it.tag == "Kermit" })
    }

    @Test
    fun `a tag whose stickers look nothing alike is not learned`() {
        val (_, vectors, tags) = world()
        // "funny" on five unrelated stickers.
        for (id in 1L..5L) tags[id] = listOf("funny")
        val result = LearnedTags.suggest(vectors, tags)
        assertTrue(result.suggestions.values.flatten().none { it.tag == "funny" })
    }

    @Test
    fun `one example spreads only to near-copies`() {
        val vectors = HashMap<Long, FloatArray>()
        for (id in 1L..100L) vectors[id] = randomVector()
        val original = randomVector()
        vectors[500L] = original
        vectors[501L] = near(original, 0.02f) // a re-saved copy
        vectors[502L] = near(original, 0.6f) // merely similar
        val result = LearnedTags.suggest(vectors, mapOf(500L to listOf("Dana")))
        assertEquals(setOf(501L), result.suggestions.keys)
    }

    @Test
    fun `keeps the spelling used most and matches tags ignoring case`() {
        val (_, vectors, tags) = world()
        tags[1001L] = listOf("kermit")
        val result = LearnedTags.suggest(vectors, tags)
        assertEquals("Kermit", result.suggestions.getValue(1005L).first().tag)
    }
}
