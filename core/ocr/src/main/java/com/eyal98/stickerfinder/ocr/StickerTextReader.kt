package com.eyal98.stickerfinder.ocr

import android.graphics.Bitmap
import java.io.Closeable

/** Reads the text printed on a sticker. Not thread-safe: use one instance per worker. */
interface StickerTextReader : Closeable {

    /** Returns the cleaned text on [sticker], or null when nothing readable was found. */
    fun read(sticker: Bitmap): String?
}
