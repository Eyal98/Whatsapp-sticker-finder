package com.eyal98.stickerfinder.ocr

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import com.googlecode.tesseract.android.TessBaseAPI
import com.googlecode.tesseract.android.TessBaseAPI.PageIteratorLevel

/**
 * On-device OCR for Hebrew and English with Tesseract. Runs fully offline.
 *
 * Stickers have transparent backgrounds, and their text is often white with a dark outline or
 * dark with a light outline. Flattening onto one background color would make one of those
 * invisible, so a sticker is read on white and on black, and the more confident reading wins.
 * Stickers that can't hide text on one of them are read once (see [OcrBackgrounds]).
 */
class TesseractTextReader private constructor(private val tess: TessBaseAPI) : StickerTextReader {

    override fun read(sticker: Bitmap): String? {
        val pixels = IntArray(sticker.width * sticker.height)
        sticker.getPixels(pixels, 0, sticker.width, 0, 0, sticker.width, sticker.height)
        val readings = OcrBackgrounds.choose(pixels).map { background ->
            readOn(sticker, if (background == OcrBackgrounds.Background.WHITE) Color.WHITE else Color.BLACK)
        }
        return OcrTextCleaner.best(*readings.toTypedArray())?.text
    }

    private fun readOn(sticker: Bitmap, background: Int): OcrTextCleaner.Reading? {
        val flat = Bitmap.createBitmap(sticker.width, sticker.height, Bitmap.Config.ARGB_8888)
        try {
            Canvas(flat).apply {
                drawColor(background)
                drawBitmap(sticker, 0f, 0f, null)
            }
            tess.setImage(flat)
            // getUTF8Text runs recognition; the iterator below reads the per-word results.
            tess.getUTF8Text()
            return OcrTextCleaner.clean(words())
        } finally {
            tess.clear()
            flat.recycle()
        }
    }

    private fun words(): List<OcrTextCleaner.Word> {
        val iterator = tess.getResultIterator() ?: return emptyList()
        val words = mutableListOf<OcrTextCleaner.Word>()
        try {
            iterator.begin()
            do {
                val text = iterator.getUTF8Text(PageIteratorLevel.RIL_WORD) ?: continue
                words += OcrTextCleaner.Word(text, iterator.confidence(PageIteratorLevel.RIL_WORD))
            } while (iterator.next(PageIteratorLevel.RIL_WORD))
        } finally {
            iterator.delete()
        }
        return words
    }

    override fun close() {
        tess.recycle()
    }

    companion object {
        private const val LANGUAGES = "heb+eng"

        /** Returns a ready reader, or null if the language files are missing or fail to load. */
        fun create(context: Context): TesseractTextReader? {
            val dataPath = TessdataInstaller.install(context) ?: return null
            val tess = TessBaseAPI()
            val ready = try {
                tess.init(dataPath.absolutePath, LANGUAGES)
            } catch (e: IllegalArgumentException) {
                // Thrown when the data path or a language file is missing.
                false
            }
            if (!ready) {
                tess.recycle()
                return null
            }
            // Sticker text is a few scattered words, not a page of paragraphs.
            tess.setPageSegMode(TessBaseAPI.PageSegMode.PSM_SPARSE_TEXT)
            return TesseractTextReader(tess)
        }
    }
}
