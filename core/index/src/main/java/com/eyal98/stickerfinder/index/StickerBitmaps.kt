package com.eyal98.stickerfinder.index

import android.content.ContentResolver
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import java.nio.ByteBuffer

internal object StickerBitmaps {

    /** WhatsApp stickers are 512×512; anything larger is scaled down to bound memory and time. */
    private const val MAX_DECODE_PX = 1024

    /** Decodes a sticker (first frame if animated) as a software bitmap that OCR and ML can read. */
    fun decode(resolver: ContentResolver, uri: Uri): Bitmap = decode(ImageDecoder.createSource(resolver, uri))

    /** Decodes a sticker already read into memory, without another trip to the file's provider. */
    fun decode(bytes: ByteArray): Bitmap = decode(ImageDecoder.createSource(ByteBuffer.wrap(bytes)))

    /**
     * The sticker's difference hash ([ImageFingerprint.dHash]) from its 9×8 thumbnail. The indexer
     * and the chat importer must compute it the same way, or the same sticker won't match.
     */
    fun perceptualHash(bitmap: Bitmap): Long {
        val w = ImageFingerprint.HASH_WIDTH
        val h = ImageFingerprint.HASH_HEIGHT
        val small = Bitmap.createScaledBitmap(bitmap, w, h, true)
        val pixels = IntArray(w * h)
        try {
            small.getPixels(pixels, 0, w, 0, 0, w, h)
        } finally {
            if (small !== bitmap) small.recycle()
        }
        return ImageFingerprint.dHash(pixels)
    }

    private fun decode(source: ImageDecoder.Source): Bitmap =
        ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            val longest = maxOf(info.size.width, info.size.height)
            if (longest > MAX_DECODE_PX) {
                val scale = MAX_DECODE_PX.toFloat() / longest
                decoder.setTargetSize(
                    (info.size.width * scale).toInt().coerceAtLeast(1),
                    (info.size.height * scale).toInt().coerceAtLeast(1),
                )
            }
        }
}
