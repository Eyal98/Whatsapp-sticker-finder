package com.eyal98.stickerfinder.index

import android.content.ContentResolver
import android.net.Uri
import android.util.Log
import com.eyal98.stickerfinder.data.StickerDao
import com.eyal98.stickerfinder.data.StickerImageVector
import com.eyal98.stickerfinder.search.Vectors
import com.eyal98.stickerfinder.vision.PictureLabels
import com.eyal98.stickerfinder.vision.SiglipImageEncoder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.IOException

/**
 * Tags stickers with the SigLIP2 image model: each sticker's image vector is compared with the
 * label vectors, and the closest labels' English and Hebrew tags become searchable. The vector is
 * kept, so a new label list only needs [retagOld], not the model.
 */
class ImageTagger(
    private val resolver: ContentResolver,
    private val dao: StickerDao,
    private val labels: PictureLabels,
    private val modelId: String,
) {

    /** Re-derives tags for stickers tagged with an older label list. Fast: no model needed. */
    suspend fun retagOld(budget: WorkBudget): Boolean = withContext(Dispatchers.Default) {
        var rows = dao.imageVectorsWithOldTags(modelId, labels.version, BATCH)
        while (rows.isNotEmpty()) {
            for (row in rows) {
                ensureActive()
                if (budget.exhausted) return@withContext false
                val tags = labels.tagsFor(Vectors.decode(row.vector)).takeIf { it.isNotEmpty() }
                withContext(NonCancellable) { dao.saveImageTags(row.stickerId, tags, labels.version, vector = null) }
            }
            rows = dao.imageVectorsWithOldTags(modelId, labels.version, BATCH)
        }
        true
    }

    /** Tags pending stickers until done or [budget] runs out. */
    suspend fun tagPending(
        encoder: SiglipImageEncoder,
        budget: WorkBudget,
        /** Checked before each sticker; true stops the run early, as if the budget ran out. */
        shouldPause: () -> Boolean = { false },
        onTagged: suspend (processed: Int) -> Unit = {},
    ): StickerIndexer.Progress = withContext(Dispatchers.Default) {
        var processed = 0
        var batch = dao.needingImageTags(BATCH)
        while (batch.isNotEmpty()) {
            for (sticker in batch) {
                ensureActive()
                if (budget.exhausted || shouldPause()) {
                    return@withContext StickerIndexer.Progress(processed, finished = false)
                }
                // Counted first, so a sticker that crashes the model is skipped after MAX_ATTEMPTS.
                dao.markImageTagAttempt(sticker.id)
                val vector = if (sticker.imageTagAttempts >= WorkBudget.MAX_ATTEMPTS) {
                    Log.w(TAG, "Sticker ${sticker.id} failed picture tagging ${sticker.imageTagAttempts} times; skipping")
                    null
                } else {
                    encode(encoder, sticker.documentUri)
                }
                val tags = vector?.let(labels::tagsFor)
                // Every outcome is saved, even "nothing", so a sticker that can't be read doesn't
                // block the ones after it. It's tried again if the file changes.
                withContext(NonCancellable) {
                    dao.saveImageTags(
                        sticker.id,
                        tags?.takeIf { it.isNotEmpty() },
                        labels.version,
                        vector?.let { StickerImageVector(sticker.id, modelId, Vectors.encode(it)) },
                    )
                }
                processed++
                onTagged(processed)
            }
            batch = dao.needingImageTags(BATCH)
        }
        StickerIndexer.Progress(processed, finished = true)
    }

    private fun encode(encoder: SiglipImageEncoder, documentUri: String): FloatArray? =
        try {
            val bitmap = StickerBitmaps.decode(resolver, Uri.parse(documentUri))
            try {
                encoder.encode(bitmap)
            } finally {
                bitmap.recycle()
            }
        } catch (e: IOException) {
            Log.w(TAG, "Could not read sticker", e)
            null
        } catch (e: SecurityException) {
            Log.w(TAG, "Lost access to sticker", e)
            null
        } catch (e: RuntimeException) {
            Log.w(TAG, "Picture tagging failed", e)
            null
        }

    private companion object {
        const val TAG = "ImageTagger"
        const val BATCH = 20
    }
}
