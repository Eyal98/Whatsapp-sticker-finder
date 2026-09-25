package com.eyal98.stickerfinder.index

import android.content.ContentResolver
import android.graphics.Bitmap
import android.net.Uri
import android.util.Log
import com.eyal98.stickerfinder.data.IndexVersion
import com.eyal98.stickerfinder.data.StickerDao
import com.eyal98.stickerfinder.data.StickerEntity
import com.eyal98.stickerfinder.data.StickerRepository
import com.eyal98.stickerfinder.ocr.StickerTextReader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.IOException

/**
 * Processes stickers that are new, changed, or indexed by an older [IndexVersion]: records whether
 * the sticker is animated, its duplicate hash, and (when [textReader] is available) the text
 * printed on it. Captions (Phase 2) plug in here too.
 */
class StickerIndexer(
    private val resolver: ContentResolver,
    private val dao: StickerDao,
    private val repository: StickerRepository,
    private val textReader: StickerTextReader?,
) {

    /**
     * Without a text reader we can only produce [IndexVersion.BASIC]. Stickers are then marked at
     * that version and not retried in this run, so the loop below always terminates.
     */
    private val version = if (textReader != null) IndexVersion.CURRENT else IndexVersion.BASIC

    /** How far a run got. */
    data class Progress(val processed: Int, val finished: Boolean)

    /** Indexes pending stickers until done or [budget] runs out. */
    suspend fun indexPending(budget: WorkBudget = WorkBudget(), batchSize: Int = 20): Progress =
        withContext(Dispatchers.IO) {
            var processed = 0
            var batch = dao.needingIndex(version, batchSize)
            while (batch.isNotEmpty()) {
                for (sticker in batch) {
                    ensureActive()
                    if (budget.exhausted) return@withContext Progress(processed, finished = false)
                    index(sticker)
                    processed++
                }
                batch = dao.needingIndex(version, batchSize)
            }
            Progress(processed, finished = true)
        }

    private suspend fun index(sticker: StickerEntity) {
        val uri = Uri.parse(sticker.documentUri)
        // Recorded before the risky native work: if decoding or OCR crashes the process or hangs
        // until the job is stopped, the next run sees the attempt, moves this sticker to the back
        // of the queue, and after MAX_ATTEMPTS skips OCR (and then decoding) for it.
        dao.markIndexAttempt(sticker.id)
        val attempts = sticker.indexAttempts
        val runOcr = attempts < WorkBudget.MAX_ATTEMPTS
        val decode = attempts <= WorkBudget.MAX_ATTEMPTS
        if (!runOcr) Log.w(TAG, "Sticker ${sticker.id} failed $attempts times; indexing it without OCR")

        // A file that can't be read is still marked as indexed so it isn't retried forever;
        // it gets another chance when its size or date changes.
        val isAnimated = runCatchingIo { readHeader(uri) }?.let(ImageFingerprint::isAnimatedWebp) ?: false
        var hash: Long? = null
        var text: String? = null
        if (decode) {
            runCatchingIo { StickerBitmaps.decode(resolver, uri) }?.let { bitmap ->
                try {
                    hash = perceptualHash(bitmap)
                    if (runOcr) text = readText(bitmap)
                } finally {
                    bitmap.recycle()
                }
            }
        }
        dao.saveIndexResult(
            id = sticker.id,
            isAnimated = isAnimated,
            perceptualHash = hash,
            ocrText = if (textReader != null) text else sticker.ocrText,
            indexedAt = System.currentTimeMillis(),
            indexVersion = version,
        )
        repository.refreshSearchTerms(sticker.id)
    }

    /**
     * A sticker that crashes the OCR engine must not stop the whole run (and every run after it,
     * since it would stay first in line), so failures here just mean "no text".
     */
    private fun readText(bitmap: Bitmap): String? =
        try {
            textReader?.read(bitmap)
        } catch (e: RuntimeException) {
            Log.w(TAG, "OCR failed", e)
            null
        }

    private fun readHeader(uri: Uri): ByteArray {
        val stream = resolver.openInputStream(uri) ?: throw IOException("Cannot open $uri")
        return stream.use {
            val buffer = ByteArray(HEADER_BYTES)
            var read = 0
            while (read < HEADER_BYTES) {
                val n = it.read(buffer, read, HEADER_BYTES - read)
                if (n < 0) break
                read += n
            }
            buffer.copyOf(read)
        }
    }

    private fun perceptualHash(bitmap: Bitmap): Long {
        val w = ImageFingerprint.HASH_WIDTH
        val h = ImageFingerprint.HASH_HEIGHT
        val small = Bitmap.createScaledBitmap(bitmap, w, h, true)
        val pixels = IntArray(w * h)
        try {
            small.getPixels(pixels, 0, w, 0, 0, w, h)
        } finally {
            if (small !== bitmap) small.recycle()
        }
        return ImageFingerprint.dHash(pixels)
    }

    private inline fun <T> runCatchingIo(block: () -> T): T? =
        try {
            block()
        } catch (e: IOException) {
            Log.w(TAG, "Could not read sticker", e)
            null
        } catch (e: SecurityException) {
            Log.w(TAG, "Lost access to sticker", e)
            null
        }

    private companion object {
        const val TAG = "StickerIndexer"
        const val HEADER_BYTES = 21
    }
}
