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
import kotlin.coroutines.cancellation.CancellationException

/** Asks the on-device model to describe stickers that don't have a caption yet. */
class CaptionIndexer(
    private val resolver: ContentResolver,
    private val dao: StickerDao,
    private val repository: StickerRepository,
    private val captioner: StickerCaptioner,
) {

    /** What happened to the stickers of one run, for the diagnostics report. */
    class Outcomes {
        var described = 0
        var empty = 0
        var errors = 0
        var lastError: String? = null
    }

    val outcomes = Outcomes()

    /** Captions pending stickers until done or [budget] runs out. */
    suspend fun captionPending(
        budget: WorkBudget = WorkBudget(),
        batchSize: Int = 10,
        onCaptioned: suspend (processed: Int) -> Unit = {},
    ): StickerIndexer.Progress =
        withContext(Dispatchers.Default) {
            var processed = 0
            var errorsInARow = 0
            var batch = dao.needingCaption(batchSize)
            while (batch.isNotEmpty()) {
                for (sticker in batch) {
                    ensureActive()
                    if (budget.exhausted) return@withContext StickerIndexer.Progress(processed, finished = false)
                    errorsInARow = if (caption(sticker)) 0 else errorsInARow + 1
                    // The model itself is failing, not one sticker: stop instead of burning
                    // through every sticker's attempts. The next run loads it again.
                    if (errorsInARow >= MAX_ERRORS_IN_A_ROW) {
                        return@withContext StickerIndexer.Progress(processed, finished = false)
                    }
                    processed++
                    onCaptioned(processed)
                }
                batch = dao.needingCaption(batchSize)
            }
            StickerIndexer.Progress(processed, finished = true)
        }

    /** False when the model threw: the sticker stays pending and is tried again later. */
    private suspend fun caption(sticker: StickerEntity): Boolean {
        // The attempt is recorded first, so a sticker that crashes the model's native code (or
        // keeps failing) is given up on after MAX_ATTEMPTS instead of blocking the others.
        dao.markCaptionAttempt(sticker.id)
        if (sticker.captionAttempts >= WorkBudget.MAX_ATTEMPTS) {
            // Keeps whatever description it had from an earlier model or prompt.
            Log.w(TAG, "Sticker ${sticker.id} failed captioning ${sticker.captionAttempts} times; skipping")
            dao.skipCaption(sticker.id, System.currentTimeMillis())
            return true
        }
        val result = try {
            val bitmap = StickerBitmaps.decode(resolver, Uri.parse(sticker.documentUri))
            try {
                captioner.caption(bitmap, sticker.ocrText, sticker.packName).also {
                    if (it == null) outcomes.empty++ else outcomes.described++
                }
            } finally {
                bitmap.recycle()
            }
        } catch (e: IOException) {
            // The file, not the model: nothing to retry.
            Log.w(TAG, "Could not read sticker", e)
            null
        } catch (e: SecurityException) {
            Log.w(TAG, "Lost access to sticker", e)
            null
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // An inference error (LiteRT-LM and MediaPipe report them as exceptions). Not saved,
            // so a passing problem doesn't leave the sticker without a description for good.
            Log.w(TAG, "Captioning failed", e)
            outcomes.errors++
            outcomes.lastError = "${e.javaClass.simpleName}: ${e.message}"
            return false
        }
        // Every other outcome is saved, even "nothing", so it isn't asked again; it's tried again
        // if the file changes. "Nothing" doesn't replace an earlier description, though.
        val hadCaption = sticker.captionEn != null || sticker.captionHe != null || sticker.captionTags != null
        if (result == null && hadCaption) {
            dao.skipCaption(sticker.id, System.currentTimeMillis())
            return true
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
        return true
    }

    private companion object {
        const val TAG = "CaptionIndexer"
        const val MAX_ERRORS_IN_A_ROW = 5
    }
}
