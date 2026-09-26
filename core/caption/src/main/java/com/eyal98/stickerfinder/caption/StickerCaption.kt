package com.eyal98.stickerfinder.caption

import android.graphics.Bitmap
import java.io.Closeable

/** A model's description of one sticker. Any field may be missing if the model skipped it. */
data class StickerCaption(
    val english: String?,
    val hebrew: String?,
    val tags: List<String>,
)

/** Describes stickers with an on-device model. Not thread-safe: use one instance per worker. */
interface StickerCaptioner : Closeable {

    /** Identifies the model, stored with each caption. */
    val modelId: String

    /** Where it runs (runtime and CPU/GPU), for the diagnostics report. */
    val setupName: String

    /**
     * @param printedText text OCR found on the sticker, given to the model as a hint.
     * @param packName the sticker pack's name from the file's metadata, also a hint.
     * @return the caption, or null if the model's reply couldn't be understood.
     */
    fun caption(sticker: Bitmap, printedText: String?, packName: String?): StickerCaption?
}
