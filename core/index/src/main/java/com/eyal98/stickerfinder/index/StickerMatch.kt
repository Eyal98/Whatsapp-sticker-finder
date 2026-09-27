package com.eyal98.stickerfinder.index

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.sqrt

/**
 * Recognizes a sticker on screen: compares a sticker's picture with a screenshot of a cell in
 * WhatsApp's sticker tray. The tray draws each sticker scaled into a square cell, with some
 * padding, over the tray's background colour, so both sides are cut down to their content (what
 * differs from that background), scaled to the same small grid and compared by correlation, in
 * colour. Pure Kotlin on ARGB pixel arrays, so it's unit-tested on the JVM.
 */
object StickerMatch {

    /** Signature grid: [SIZE]×[SIZE] cells, three colour channels each. */
    const val SIZE = 32

    /** At or above this, a cell shows the sticker (identical pictures score about 0.98 or more). */
    const val SAME_PICTURE = 0.9f

    /**
     * How far the best cell must be ahead of the next one. Packs often hold near-identical
     * stickers (the same face with another caption): when two cells are that close, it's not
     * certain which one it is, and the keyboard sends a copy rather than risk the wrong sticker.
     */
    const val MARGIN = 0.02f

    /** Whether [best] (and [next], the runner-up if any) identify one cell with confidence. */
    fun isConfident(best: Float, next: Float?): Boolean =
        best >= SAME_PICTURE && (next == null || best - next >= MARGIN)

    /** How far a pixel's colour must be from the background to count as content (0..255). */
    private const val CONTENT_DIFFERENCE = 28

    /**
     * The signature of the content of an image of [width]×[height] ARGB [pixels], drawn over
     * [background] (transparent parts show the background, as in the tray). Null when nothing
     * differs from the background.
     */
    fun signature(pixels: IntArray, width: Int, height: Int, background: Int): FloatArray? {
        require(pixels.size == width * height) { "Expected ${width}x$height pixels" }
        val bgR = (background shr 16) and 0xFF
        val bgG = (background shr 8) and 0xFF
        val bgB = background and 0xFF
        val opaque = IntArray(pixels.size)
        var left = width
        var top = height
        var right = -1
        var bottom = -1
        for (y in 0 until height) {
            for (x in 0 until width) {
                val p = pixels[y * width + x]
                val a = (p ushr 24) and 0xFF
                val r = (((p shr 16) and 0xFF) * a + bgR * (255 - a)) / 255
                val g = (((p shr 8) and 0xFF) * a + bgG * (255 - a)) / 255
                val b = ((p and 0xFF) * a + bgB * (255 - a)) / 255
                opaque[y * width + x] = (r shl 16) or (g shl 8) or b
                if (max(abs(r - bgR), max(abs(g - bgG), abs(b - bgB))) > CONTENT_DIFFERENCE) {
                    if (x < left) left = x
                    if (x > right) right = x
                    if (y < top) top = y
                    if (y > bottom) bottom = y
                }
            }
        }
        if (right < left || bottom < top) return null

        // Average the content box down to SIZE×SIZE (box filter), per channel.
        val out = FloatArray(SIZE * SIZE * 3)
        val boxW = right - left + 1
        val boxH = bottom - top + 1
        for (gy in 0 until SIZE) {
            val y0 = top + gy * boxH / SIZE
            val y1 = max(y0 + 1, top + (gy + 1) * boxH / SIZE)
            for (gx in 0 until SIZE) {
                val x0 = left + gx * boxW / SIZE
                val x1 = max(x0 + 1, left + (gx + 1) * boxW / SIZE)
                var r = 0L
                var g = 0L
                var b = 0L
                var n = 0
                for (y in y0 until y1) {
                    for (x in x0 until x1) {
                        val p = opaque[y * width + x]
                        r += (p shr 16) and 0xFF
                        g += (p shr 8) and 0xFF
                        b += p and 0xFF
                        n++
                    }
                }
                val i = (gy * SIZE + gx) * 3
                out[i] = r.toFloat() / n
                out[i + 1] = g.toFloat() / n
                out[i + 2] = b.toFloat() / n
            }
        }
        // Zero mean, unit length: the dot product of two signatures is their correlation.
        val mean = out.average().toFloat()
        var norm = 0.0
        for (i in out.indices) {
            out[i] -= mean
            norm += out[i] * out[i]
        }
        val length = sqrt(norm).toFloat()
        if (length == 0f) return null
        for (i in out.indices) out[i] /= length
        return out
    }

    /** Correlation of two signatures, from -1 to 1. */
    fun similarity(a: FloatArray, b: FloatArray): Float {
        require(a.size == b.size)
        var dot = 0f
        for (i in a.indices) dot += a[i] * b[i]
        return dot
    }
}
