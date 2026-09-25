package com.eyal98.stickerfinder.ocr

import com.eyal98.stickerfinder.ocr.OcrBackgrounds.Background.BLACK
import com.eyal98.stickerfinder.ocr.OcrBackgrounds.Background.WHITE
import org.junit.Assert.assertEquals
import org.junit.Test

class OcrBackgroundsTest {

    private val transparent = 0x00FFFFFF
    private val white = 0xFFFFFFFF.toInt()
    private val black = 0xFF000000.toInt()
    private val red = 0xFFE53935.toInt()

    private fun sticker(vararg parts: Pair<Int, Int>): IntArray =
        parts.flatMap { (color, count) -> List(count) { color } }.toIntArray()

    @Test
    fun noNearWhitePixels_readsOnWhiteOnly() {
        assertEquals(listOf(WHITE), OcrBackgrounds.choose(sticker(transparent to 5000, black to 800, red to 800)))
    }

    @Test
    fun noNearBlackPixels_readsOnBlackOnly() {
        assertEquals(listOf(BLACK), OcrBackgrounds.choose(sticker(transparent to 5000, white to 800, red to 800)))
    }

    @Test
    fun bothExtremes_readsOnBoth() {
        assertEquals(listOf(WHITE, BLACK), OcrBackgrounds.choose(sticker(white to 800, black to 800)))
    }

    @Test
    fun transparentPixelsAreIgnored() {
        // Fully transparent white pixels are the background, not white text.
        assertEquals(listOf(WHITE), OcrBackgrounds.choose(sticker(transparent to 100_000, black to 500)))
    }

    @Test
    fun aFewStrayPixelsDontCount() {
        val few = OcrBackgrounds.MIN_PIXELS - 1
        assertEquals(listOf(WHITE), OcrBackgrounds.choose(sticker(black to 800, white to few)))
    }
}
