package com.eyal98.stickerfinder.vision

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest

class PictureLabelsTest {

    private val tsv = """
        # comment
        a cat	cat, kitty	חתול
        a dog	dog	כלב

        a red heart	heart, love	לב, אהבה
    """.trimIndent()

    /** Builds a label vector file the way build_labels.py does, with one-hot 4-d rows. */
    private fun bin(prompts: List<String>, dim: Int = 4): ByteArray {
        val buffer = ByteBuffer.allocate(52 + prompts.size * dim * 2).order(ByteOrder.LITTLE_ENDIAN)
        buffer.put("SLB1".toByteArray()).putInt(prompts.size).putInt(dim).putFloat(10f).putFloat(-10f)
        buffer.put(MessageDigest.getInstance("SHA-256").digest(prompts.joinToString("\n").toByteArray()))
        for (row in prompts.indices) {
            for (i in 0 until dim) buffer.putShort(if (i == row) 0x3C00.toShort() else 0) // 1.0 or 0.0
        }
        return buffer.array()
    }

    private val prompts = listOf("a cat", "a dog", "a red heart")

    @Test
    fun parsesAndTagsInBothLanguages() {
        val labels = PictureLabels.parse(tsv, bin(prompts))
        assertEquals(3, labels.count)
        assertEquals(4, labels.dim)
        assertEquals("heart, love, לב, אהבה", labels.tagsFor(floatArrayOf(0f, 0f, 1f, 0f)))
    }

    @Test
    fun rejectsVectorsForADifferentLabelList() {
        assertThrows(IllegalArgumentException::class.java) {
            PictureLabels.parse(tsv, bin(listOf("a cat", "a puppy", "a red heart")))
        }
    }

    @Test
    fun picksCloseLabelsOnly() {
        val picked = PictureLabels.pick(floatArrayOf(0.20f, 0.19f, 0.10f, 0.05f))
        assertEquals(listOf(0, 1), picked)
    }

    @Test
    fun namesArePickedSeparatelyAndNeedACloserMatch() {
        val isName = booleanArrayOf(true, false, false, true)
        // A name close to the image comes first, and the best other label still follows it.
        assertEquals(listOf(0, 1), PictureLabels.pick(floatArrayOf(0.20f, 0.15f, 0.12f, 0.10f), isName))
        // A weak name match is left out even though an ordinary label that weak would be kept.
        assertEquals(listOf(1), PictureLabels.pick(floatArrayOf(0.13f, 0.13f, 0.05f, 0.05f), isName))
    }

    @Test
    fun parsesTheNameColumn() {
        val labels = PictureLabels.parse("a cat\tcat\tחתול\nPikachu\tpikachu\tפיקאצ'ו\tname", bin(listOf("a cat", "Pikachu")))
        assertEquals("pikachu, פיקאצ'ו, cat, חתול", labels.tagsFor(floatArrayOf(0.5f, 0.5f, 0f, 0f).normalized()))
    }

    private fun FloatArray.normalized(): FloatArray {
        val n = kotlin.math.sqrt(sumOf { (it * it).toDouble() }).toFloat()
        return FloatArray(size) { this[it] / n }
    }

    @Test
    fun picksNothingWhenNothingFits() {
        assertTrue(PictureLabels.pick(floatArrayOf(0.02f, 0.01f)).isEmpty())
    }

    @Test
    fun picksAtMostMaxLabels() {
        val picked = PictureLabels.pick(FloatArray(20) { 0.3f })
        assertEquals(PictureLabels.MAX_LABELS, picked.size)
    }

    @Test
    fun halfFloats() {
        assertEquals(1.0f, PictureLabels.halfToFloat(0x3C00), 0f)
        assertEquals(-2.0f, PictureLabels.halfToFloat(0xC000.toShort()), 0f)
        assertEquals(0.5f, PictureLabels.halfToFloat(0x3800), 0f)
        assertEquals(5.9604645e-8f, PictureLabels.halfToFloat(0x0001), 1e-12f)
        assertEquals(0f, PictureLabels.halfToFloat(0), 0f)
    }
}
