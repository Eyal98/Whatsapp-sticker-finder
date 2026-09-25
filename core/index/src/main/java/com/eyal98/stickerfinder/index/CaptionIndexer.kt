package com.eyal98.stickerfinder.index

import android.content.ContentResolver
import android.net.Uri
import android.util.Log
import com.eyal98.stickerfinder.caption.StickerCaptioner
import com.eyal98.stickerfinder.data.StickerDao
import com.eyal98.stickerfinder.data.StickerEntity
import com.eyal98.stickerfinder.data.StickerRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.IOException

/** Asks the on-device model to describe stickers that don't have a caption yet. */
class CaptionIndexer(
    private val resolver: ContentResolver,
    private val dao: StickerDao,
    private val repository: StickerRepository,
    private val captioner: StickerCaptioner,
) {

    /** Captions pending stickers until done or [budget] runs out. */
    suspend fun captionPending(
        budget: WorkBudget = WorkBudget(),
        batchSize: Int = 10,
        onCaptioned: suspend (processed: Int) -> Unit = {},
    ): StickerIndexer.Progress =
        withContext(Dispatchers.Default) {
            var processed = 0
            var batch = dao.needingCaption(batchSize)
            while (batch.isNotEmpty()) {
                for (sticker in batch) {
                    ensureActive()
                    if (budget.exhausted) return@withContext StickerIndexer.Progress(processed, finished = false)
                    caption(sticker)
                    processed++
                    onCaptioned(processed)
                }
                batch = dao.needingCaption(batchSize)
            }
            StickerIndexer.Progress(processed, finished = true)
        }

    private suspend fun caption(sticker: StickerEntity) {
        // Every outcome is saved, even "nothing", so a sticker the model can't handle doesn't
        // block the ones after it. It's tried again if the file changes. The attempt is recorded
        // first, so one that crashes the model's native code is given up on after MAX_ATTEMPTS.
        dao.markCaptionAttempt(sticker.id)
        val result = if (sticker.captionAttempts >= WorkBudget.MAX_ATTEMPTS) {
            Log.w(TAG, "Sticker ${sticker.id} failed captioning ${sticker.captionAttempts} times; skipping")
            null
        } else try {
            val bitmap = StickerBitmaps.decode(resolver, Uri.parse(sticker.documentUri))
            try {
                captioner.caption(bitmap, sticker.ocrText)
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
            // MediaPipe reports inference errors as runtime exceptions.
            Log.w(TAG, "Captioning failed", e)
            null
        }
        dao.saveCaption(
            id = sticker.id,
            en = result?.english,
            he = result?.hebrew,
            tags = result?.tags?.takeIf { it.isNotEmpty() }?.joinToString(", "),
            at = System.currentTimeMillis(),
            model = captioner.modelId,
        )
        repository.refreshSearchTerms(sticker.id)
    }

    private companion object {
        const val TAG = "CaptionIndexer"
    }
}
