package com.eyal98.stickerfinder.ocr

/**
 * Picks the backgrounds a sticker needs to be read on. On white, only near-white text disappears;
 * on black, only near-black text does. So when a sticker has no near-white pixels, the white
 * background alone shows everything, and the same for black. Only stickers with both need two
 * readings, which halves the OCR time for the others.
 */
object OcrBackgrounds {

    enum class Background { WHITE, BLACK }

    /** Fewer pixels than this can't form a readable word (a stray edge or highlight). */
    const val MIN_PIXELS = 64

    private const val OPAQUE_ALPHA = 128
    private const val NEAR_WHITE = 200
    private const val NEAR_BLACK = 55

    /** @param argb the sticker's pixels, as returned by Bitmap.getPixels. */
    fun choose(argb: IntArray): List<Background> {
        var nearWhite = 0
        var nearBlack = 0
        for (p in argb) {
            if ((p ushr 24) < OPAQUE_ALPHA) continue
            val luma = (299 * ((p shr 16) and 0xFF) + 587 * ((p shr 8) and 0xFF) + 114 * (p and 0xFF)) / 1000
            if (luma >= NEAR_WHITE) nearWhite++ else if (luma <= NEAR_BLACK) nearBlack++
        }
        return when {
            nearWhite < MIN_PIXELS -> listOf(Background.WHITE)
            nearBlack < MIN_PIXELS -> listOf(Background.BLACK)
            else -> listOf(Background.WHITE, Background.BLACK)
        }
    }
}
