package com.eyal98.stickerfinder.index

import android.content.ContentResolver
import android.graphics.Bitmap
import android.net.Uri
import android.util.Log
import com.eyal98.stickerfinder.data.IndexResult
import com.eyal98.stickerfinder.data.IndexVersion
import com.eyal98.stickerfinder.data.StickerDao
import com.eyal98.stickerfinder.data.StickerEntity
import com.eyal98.stickerfinder.ocr.StickerTextReader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.util.concurrent.ConcurrentLinkedQueue

/**
 * Processes stickers that are new, changed, or indexed by an older [IndexVersion]: records whether
 * the sticker is animated, its duplicate hash, and (when [textReaders] is not empty) the text
 * printed on it.
 *
 * OCR takes most of the time and uses one CPU core per reader, so stickers are indexed in
 * parallel, one per reader. Stickers that failed before are indexed one at a time, so if one
 * crashes the app again, no other sticker is blamed for it.
 */
class StickerIndexer(
    private val resolver: ContentResolver,
    private val dao: StickerDao,
    /** One per parallel worker; a reader is used by one sticker at a time. */
    private val textReaders: List<StickerTextReader>,
) {

    /**
     * Without a text reader we can only produce [IndexVersion.BASIC]. Stickers are then marked at
     * that version and not retried in this run, so the loop below always terminates.
     */
    private val version = if (textReaders.isNotEmpty()) IndexVersion.CURRENT else IndexVersion.BASIC

    /** How far a run got. */
    data class Progress(val processed: Int, val finished: Boolean)

    /** Indexes pending stickers until done or [budget] runs out. */
    suspend fun indexPending(
        budget: WorkBudget = WorkBudget(),
        batchSize: Int = BATCH_PER_READER * textReaders.size.coerceAtLeast(1),
        onBatch: suspend (processed: Int) -> Unit = {},
    ): Progress =
        withContext(Dispatchers.IO) {
            val readers: List<StickerTextReader?> = textReaders.ifEmpty { listOf(null) }
            var processed = 0
            var batch = dao.needingIndex(version, batchSize)
            while (batch.isNotEmpty()) {
                val (fresh, retried) = batch.partition { it.indexAttempts == 0 }
                val results = ConcurrentLinkedQueue<IndexResult>()
                try {
                    coroutineScope {
                        val queue = Channel<StickerEntity>(Channel.UNLIMITED)
                        fresh.forEach { queue.trySend(it) }
                        queue.close()
                        for (reader in readers) {
                            launch {
                                for (sticker in queue) {
                                    if (budget.exhausted) break
                                    results += index(sticker, reader)
                                }
                            }
                        }
                    }
                    for (sticker in retried) {
                        ensureActive()
                        if (budget.exhausted) break
                        results += index(sticker, readers.first())
                    }
                } finally {
                    // Saved in one transaction per batch, and even if the job is being stopped,
                    // so finished work isn't lost and an interrupted run isn't mistaken for the
                    // stickers failing. Success also clears the attempt count.
                    withContext(NonCancellable) { dao.saveIndexResults(results.toList()) }
                }
                processed += results.size
                if (budget.exhausted) return@withContext Progress(processed, finished = false)
                onBatch(processed)
                batch = dao.needingIndex(version, batchSize)
            }
            Progress(processed, finished = true)
        }

    private suspend fun index(sticker: StickerEntity, textReader: StickerTextReader?): IndexResult {
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
        // The file is read once and decoded from memory: every read through the storage provider
        // is a call into another process, and Android kills apps that make too many of those.
        val bytes = runCatchingIo { if (decode) readAll(uri) else readHeader(uri) }
        val isAnimated = bytes?.let(ImageFingerprint::isAnimatedWebp) ?: false
        var hash: Long? = null
        var text: String? = null
        if (decode && bytes != null) {
            runCatchingIo { StickerBitmaps.decode(bytes) }?.let { bitmap ->
                try {
                    hash = perceptualHash(bitmap)
                    if (runOcr && textReader != null) text = readText(textReader, bitmap)
                } finally {
                    bitmap.recycle()
                }
            }
        }
        return IndexResult(
            id = sticker.id,
            isAnimated = isAnimated,
            perceptualHash = hash,
            ocrText = if (textReader != null) text else sticker.ocrText,
            indexedAt = System.currentTimeMillis(),
            indexVersion = version,
        )
    }

    private fun readAll(uri: Uri): ByteArray {
        val stream = resolver.openInputStream(uri) ?: throw IOException("Cannot open $uri")
        return stream.use {
            val bytes = it.readNBytesCompat(MAX_FILE_BYTES + 1)
            if (bytes.size > MAX_FILE_BYTES) throw IOException("Sticker file too large")
            bytes
        }
    }

    /** InputStream.readNBytes needs API 33; minSdk is 30. */
    private fun InputStream.readNBytesCompat(limit: Int): ByteArray {
        val out = ByteArrayOutputStream()
        val buffer = ByteArray(64 * 1024)
        while (out.size() < limit) {
            val n = read(buffer, 0, minOf(buffer.size, limit - out.size()))
            if (n < 0) break
            out.write(buffer, 0, n)
        }
        return out.toByteArray()
    }

    /**
     * A sticker that crashes the OCR engine must not stop the whole run (and every run after it,
     * since it would stay first in line), so failures here just mean "no text".
     */
    private fun readText(textReader: StickerTextReader, bitmap: Bitmap): String? =
        try {
            textReader.read(bitmap)
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

        /** Enough stickers per batch to keep every reader busy between saves. */
        const val BATCH_PER_READER = 8

        /** WhatsApp stickers are at most a few hundred KB; this guards memory against odd files. */
        const val MAX_FILE_BYTES = 8 * 1024 * 1024
    }
}
