package com.eyal98.stickerfinder.index

import android.content.ContentResolver
import android.graphics.Bitmap
import android.graphics.ImageDecoder
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

    /** Indexes everything pending and returns how many stickers were processed. */
    suspend fun indexPending(batchSize: Int = 20): Int = withContext(Dispatchers.IO) {
        var processed = 0
        while (true) {
            val batch = dao.needingIndex(version, batchSize)
            if (batch.isEmpty()) break
            for (sticker in batch) {
                ensureActive()
                index(sticker)
                processed++
            }
        }
        processed
    }

    private suspend fun index(sticker: StickerEntity) {
        val uri = Uri.parse(sticker.documentUri)
        // A file that can't be read is still marked as indexed so it isn't retried forever;
        // it gets another chance when its size or date changes.
        val isAnimated = runCatchingIo { readHeader(uri) }?.let(ImageFingerprint::isAnimatedWebp) ?: false
        var hash: Long? = null
        var text: String? = null
        runCatchingIo { decode(uri) }?.let { bitmap ->
            try {
                hash = perceptualHash(bitmap)
                text = readText(bitmap)
            } finally {
                bitmap.recycle()
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

    /** Decodes the sticker (first frame if animated) as a software bitmap that OCR can read. */
    private fun decode(uri: Uri): Bitmap =
        ImageDecoder.decodeBitmap(ImageDecoder.createSource(resolver, uri)) { decoder, info, _ ->
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            // WhatsApp stickers are 512×512; cap anything larger to bound memory and OCR time.
            val longest = maxOf(info.size.width, info.size.height)
            if (longest > MAX_DECODE_PX) {
                val scale = MAX_DECODE_PX.toFloat() / longest
                decoder.setTargetSize(
                    (info.size.width * scale).toInt().coerceAtLeast(1),
                    (info.size.height * scale).toInt().coerceAtLeast(1),
                )
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
        const val MAX_DECODE_PX = 1024
    }
}
