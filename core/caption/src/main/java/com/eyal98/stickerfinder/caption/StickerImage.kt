package com.eyal98.stickerfinder.caption

import android.graphics.Bitmap
import android.graphics.Canvas

internal object StickerImage {

    /**
     * Stickers are transparent. On white, white text disappears; on black, black text does.
     * A mid-gray background keeps both readable for the model.
     */
    private const val BACKGROUND = 0xFF9E9E9E.toInt()

    /** Draws [sticker] on an opaque background. The caller recycles the result. */
    fun flatten(sticker: Bitmap): Bitmap {
        val flat = Bitmap.createBitmap(sticker.width, sticker.height, Bitmap.Config.ARGB_8888)
        Canvas(flat).apply {
            drawColor(BACKGROUND)
            drawBitmap(sticker, 0f, 0f, null)
        }
        return flat
    }
}
