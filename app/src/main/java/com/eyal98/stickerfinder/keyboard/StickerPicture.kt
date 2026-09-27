package com.eyal98.stickerfinder.keyboard

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import java.io.IOException

/** A sticker's picture (the first frame if animated), small, for recognizing it on screen. */
internal object StickerPicture {

    private const val MAX_SIDE = 192

    /** Decodes off the main thread; null if it can't be read. */
    fun decode(context: Context, documentUri: String): Bitmap? = try {
        val source = ImageDecoder.createSource(context.contentResolver, Uri.parse(documentUri))
        ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            val side = maxOf(info.size.width, info.size.height)
            if (side > MAX_SIDE) {
                decoder.setTargetSize(info.size.width * MAX_SIDE / side, info.size.height * MAX_SIDE / side)
            }
        }.let { if (it.config == Bitmap.Config.ARGB_8888) it else it.copy(Bitmap.Config.ARGB_8888, false).also { _ -> it.recycle() } }
    } catch (e: IOException) {
        null
    } catch (e: SecurityException) {
        null
    }
}
