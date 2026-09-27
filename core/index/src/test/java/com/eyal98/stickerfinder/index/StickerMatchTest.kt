package com.eyal98.stickerfinder.index

import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StickerMatchTest {

    private val red = 0xFFE53935.toInt()
    private val blue = 0xFF1E88E5.toInt()
    private val yellow = 0xFFFDD835.toInt()
    private val light = 0xFFFFFFFF.toInt()
    private val dark = 0xFF1F2C34.toInt()

    /** A 64×64 sticker on a transparent background: a disc in [disc] and a square in [square]. */
    private fun sticker(disc: Int, square: Int, squareLeft: Boolean = false): IntArray = IntArray(64 * 64) { i ->
        val x = i % 64
        val y = i / 64
        val dx = x - 40
        val dy = y - 24
        val sx = if (squareLeft) 6 else 34
        when {
            dx * dx + dy * dy < 15 * 15 -> disc
            x in sx until sx + 22 && y in 38 until 60 -> square
            else -> 0 // transparent
        }
    }

    /** How the tray shows it: scaled into a [cell]-pixel square with [pad] pixels around, over [bg]. */
    private fun trayCell(sticker: IntArray, cell: Int, pad: Int, bg: Int): IntArray = IntArray(cell * cell) { i ->
        val x = i % cell - pad
        val y = i / cell - pad
        val inner = cell - 2 * pad
        if (x !in 0 until inner || y !in 0 until inner) return@IntArray bg
        val p = sticker[(y * 64 / inner) * 64 + (x * 64 / inner)]
        if ((p ushr 24) == 0) bg else p
    }

    private fun score(stickerPixels: IntArray, cellPixels: IntArray, cell: Int, bg: Int): Float {
        val a = StickerMatch.signature(stickerPixels, 64, 64, bg)!!
        val b = StickerMatch.signature(cellPixels, cell, cell, bg)!!
        return StickerMatch.similarity(a, b)
    }

    @Test
    fun `the same sticker in a tray cell matches, on light and dark trays`() {
        val s = sticker(red, blue)
        for (bg in listOf(light, dark)) {
            val sim = score(s, trayCell(s, 150, 14, bg), 150, bg)
            assertTrue("same sticker scored $sim", sim >= StickerMatch.SAME_PICTURE)
        }
    }

    @Test
    fun `a different sticker from the same pack doesn't match`() {
        val s = sticker(red, blue)
        val recoloured = sticker(yellow, blue)
        val moved = sticker(red, blue, squareLeft = true)
        for (other in listOf(recoloured, moved)) {
            val sim = score(s, trayCell(other, 150, 14, dark), 150, dark)
            assertTrue("different sticker scored $sim", sim < StickerMatch.SAME_PICTURE)
        }
    }

    @Test
    fun `a close runner-up isn't confident`() {
        assertTrue(StickerMatch.isConfident(0.98f, 0.7f))
        assertTrue(StickerMatch.isConfident(0.95f, null))
        assertFalse(StickerMatch.isConfident(0.97f, 0.96f))
        assertFalse(StickerMatch.isConfident(0.85f, 0.2f))
    }

    @Test
    fun `an empty cell has no signature`() {
        assertNull(StickerMatch.signature(IntArray(100) { dark }, 10, 10, dark))
        assertNull(StickerMatch.signature(IntArray(100) { 0 }, 10, 10, dark))
    }
}
