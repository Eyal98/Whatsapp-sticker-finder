package com.eyal98.stickerfinder.index

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream

class StickerMetadataTest {

    private fun le32(n: Int) = byteArrayOf(n.toByte(), (n shr 8).toByte(), (n shr 16).toByte(), (n shr 24).toByte())

    private fun chunk(tag: String, data: ByteArray): ByteArray {
        val out = ByteArrayOutputStream()
        out.write(tag.toByteArray())
        out.write(le32(data.size))
        out.write(data)
        if (data.size % 2 == 1) out.write(0)
        return out.toByteArray()
    }

    /** A WebP the way sticker apps write it: image data, then EXIF with the JSON in a TIFF tag. */
    private fun webp(json: String?): ByteArray {
        val body = ByteArrayOutputStream()
        body.write("WEBP".toByteArray())
        body.write(chunk("VP8X", ByteArray(10)))
        body.write(chunk("VP8L", ByteArray(7) { 0x2F }))
        if (json != null) {
            val tiff = byteArrayOf(0x49, 0x49, 0x2A, 0, 8, 0, 0, 0, 1, 0, 0x41, 0x57, 7, 0) +
                le32(json.toByteArray().size) + le32(22) + json.toByteArray()
            body.write(chunk("EXIF", tiff))
        }
        val bytes = body.toByteArray()
        return "RIFF".toByteArray() + le32(bytes.size) + bytes
    }

    @Test
    fun `reads pack name, publisher and emoji words`() {
        val meta = StickerMetadata.read(
            webp("""{"sticker-pack-id":"x","sticker-pack-name":"Friends \"TV\"","sticker-pack-publisher":"Dana","emojis":["😂","❤️"]}"""),
        )
        assertEquals("Friends \"TV\"", meta?.packName)
        assertEquals("Dana", meta?.publisher)
        assertTrue("laughing" in meta!!.emojiWords)
        assertTrue("אהבה" in meta.emojiWords)
    }

    @Test
    fun `decodes escaped Hebrew`() {
        val meta = StickerMetadata.read(webp("""{"sticker-pack-name":"הפשוטע"}"""))
        assertEquals("הפשוטע", meta?.packName)
    }

    @Test
    fun `skin tones don't hide the emoji`() {
        assertEquals(StickerMetadata.read(webp("""{"emojis":["👍"]}"""))?.emojiWords, EmojiWords.of("👍🏽"))
    }

    @Test
    fun `no metadata is null`() {
        assertNull(StickerMetadata.read(webp(null)))
        assertNull(StickerMetadata.read(webp("""{"sticker-pack-id":"x"}""")))
        assertNull(StickerMetadata.read(byteArrayOf(1, 2, 3)))
    }
}
