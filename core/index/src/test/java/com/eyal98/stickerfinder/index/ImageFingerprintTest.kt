package com.eyal98.stickerfinder.index

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ImageFingerprintTest {

    private val size = ImageFingerprint.HASH_WIDTH * ImageFingerprint.HASH_HEIGHT

    @Test
    fun `flat image hashes to zero`() {
        assertEquals(0L, ImageFingerprint.dHash(IntArray(size) { 0xFF808080.toInt() }))
    }

    @Test
    fun `left-to-right fade sets every bit`() {
        val pixels = IntArray(size) { i ->
            val v = 255 - (i % ImageFingerprint.HASH_WIDTH) * 20
            (0xFF shl 24) or (v shl 16) or (v shl 8) or v
        }
        assertEquals(-1L, ImageFingerprint.dHash(pixels))
    }

    @Test
    fun `transparent pixels count as white`() {
        val transparent = IntArray(size) { 0 }
        val white = IntArray(size) { 0xFFFFFFFF.toInt() }
        assertEquals(ImageFingerprint.dHash(white), ImageFingerprint.dHash(transparent))
    }

    @Test
    fun `detects animation flag in VP8X header`() {
        val header = "RIFF\u0000\u0000\u0000\u0000WEBPVP8X\u0000\u0000\u0000\u0000".toByteArray(Charsets.US_ASCII) +
            byteArrayOf(0x02)
        assertTrue(ImageFingerprint.isAnimatedWebp(header))
        header[20] = 0x10
        assertFalse(ImageFingerprint.isAnimatedWebp(header))
    }

    @Test
    fun `short or non-WebP header is not animated`() {
        assertFalse(ImageFingerprint.isAnimatedWebp(ByteArray(4)))
        assertFalse(ImageFingerprint.isAnimatedWebp(ByteArray(21)))
    }
}
