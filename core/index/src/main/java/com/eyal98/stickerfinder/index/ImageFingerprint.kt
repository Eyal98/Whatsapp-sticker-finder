package com.eyal98.stickerfinder.index

/** Pure image helpers, kept free of Android types so they can be unit-tested on the JVM. */
object ImageFingerprint {

    const val HASH_WIDTH = 9
    const val HASH_HEIGHT = 8

    /**
     * Difference hash of a [HASH_WIDTH]×[HASH_HEIGHT] ARGB image: bit i is set when a pixel is
     * brighter than its right neighbour. Transparent pixels count as white, since stickers are
     * shown on a light background.
     */
    fun dHash(argb: IntArray): Long {
        require(argb.size == HASH_WIDTH * HASH_HEIGHT) { "Expected ${HASH_WIDTH}x$HASH_HEIGHT pixels" }
        var hash = 0L
        var bit = 0
        for (y in 0 until HASH_HEIGHT) {
            for (x in 0 until HASH_WIDTH - 1) {
                val left = luminanceOnWhite(argb[y * HASH_WIDTH + x])
                val right = luminanceOnWhite(argb[y * HASH_WIDTH + x + 1])
                if (left > right) hash = hash or (1L shl bit)
                bit++
            }
        }
        return hash
    }

    private fun luminanceOnWhite(argb: Int): Int {
        val a = (argb ushr 24) and 0xFF
        val r = (argb shr 16) and 0xFF
        val g = (argb shr 8) and 0xFF
        val b = argb and 0xFF
        val lum = (r * 299 + g * 587 + b * 114) / 1000
        return (lum * a + 255 * (255 - a)) / 255
    }

    /**
     * True when a WebP file header declares animation. Needs the first 21 bytes: a `RIFF....WEBP`
     * container whose first chunk is `VP8X`, with the animation flag (0x02) set in its flags byte.
     */
    fun isAnimatedWebp(header: ByteArray): Boolean {
        if (header.size < 21) return false
        fun ascii(from: Int) = String(header, from, 4, Charsets.US_ASCII)
        return ascii(0) == "RIFF" && ascii(8) == "WEBP" && ascii(12) == "VP8X" &&
            (header[20].toInt() and 0x02) != 0
    }
}
