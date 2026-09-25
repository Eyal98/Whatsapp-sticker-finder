package com.eyal98.stickerfinder.caption

import android.content.Context
import com.eyal98.stickerfinder.ml.InstalledModel
import java.io.File

/** Opens a caption model with the runtime that matches its file format. */
object StickerCaptioners {

    /** Loads the model. Slow (seconds) and memory-hungry: call from a background worker. */
    fun create(context: Context, model: InstalledModel): StickerCaptioner =
        if (isZip(model.file)) {
            // MediaPipe .task files are zip archives.
            MediaPipeCaptioner.create(context, model)
        } else {
            // .litertlm files. Anything else fails to load here rather than crash MediaPipe.
            LiteRtLmCaptioner.create(context, model)
        }

    /**
     * Decided by content, not name: the installed file keeps a fixed name, and models imported
     * before .litertlm support may be either format.
     */
    private fun isZip(file: File): Boolean {
        val header = ByteArray(4)
        val read = file.inputStream().use { it.read(header) }
        return read == 4 && header[0] == 'P'.code.toByte() && header[1] == 'K'.code.toByte() &&
            header[2] == 3.toByte() && header[3] == 4.toByte()
    }
}
