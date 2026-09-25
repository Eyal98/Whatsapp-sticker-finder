package com.eyal98.stickerfinder.caption

import android.content.Context
import com.eyal98.stickerfinder.ml.InstalledModel
import com.eyal98.stickerfinder.ml.ModelStore

/** Opens a caption model with the runtime that matches its file format. */
object StickerCaptioners {

    /** Loads the model. Slow (seconds) and memory-hungry: call from a background worker. */
    fun create(context: Context, model: InstalledModel): StickerCaptioner =
        // Decided by content: MediaPipe .task files are zip archives, .litertlm files aren't.
        if (ModelStore.isZip(model.file)) {
            MediaPipeCaptioner.create(context, model)
        } else {
            LiteRtLmCaptioner.create(context, model)
        }
}
