package com.eyal98.stickerfinder.caption

import android.content.Context
import com.eyal98.stickerfinder.ml.InstalledModel
import com.eyal98.stickerfinder.ml.ModelStore

/** Opens the installed caption model. */
object StickerCaptioners {

    /** Loads the model. Slow (seconds) and memory-hungry: call from a background worker. */
    fun create(context: Context, model: InstalledModel): StickerCaptioner {
        // MediaPipe .task files (zip archives) were supported until its runtime was dropped to
        // halve the app's size. One installed before then is reported, not opened.
        require(!ModelStore.isZip(model.file)) {
            ".task model files are no longer supported: remove it and import gemma-4-E2B-it.litertlm"
        }
        return LiteRtLmCaptioner.create(context, model)
    }
}
