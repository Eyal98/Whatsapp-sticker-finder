package com.eyal98.stickerfinder.index

import android.content.ContentResolver
import android.graphics.ImageDecoder
import android.net.Uri
import android.util.Log
import com.eyal98.stickerfinder.data.StickerDao
import com.eyal98.stickerfinder.data.StickerEntity
import com.eyal98.stickerfinder.data.StickerRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.IOException

/**
 * Processes stickers that are new or changed. Phase 1 records whether the sticker is animated and
 * its duplicate hash. OCR (Phase 1b, Tesseract heb+eng) and captions (Phase 2) plug in here.
 */
class StickerIndexer(
    private val resolver: ContentResolver,
    private val dao: StickerDao,
    private val repository: StickerRepository,
) {

    /** Indexes everything pending and returns how many stickers were processed. */
    suspend fun indexPending(batchSize: Int = 50): Int = withContext(Dispatchers.IO) {
        var processed = 0
        while (true) {
            val batch = dao.needingIndex(batchSize)
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
        val hash = runCatchingIo { perceptualHash(uri) }
        dao.saveIndexResult(
            id = sticker.id,
            isAnimated = isAnimated,
            perceptualHash = hash,
            ocrText = sticker.ocrText,
            indexedAt = System.currentTimeMillis(),
        )
        repository.refreshSearchTerms(sticker.id)
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

    private fun perceptualHash(uri: Uri): Long {
        val source = ImageDecoder.createSource(resolver, uri)
        // For animated WebP this decodes the first frame.
        val bitmap = ImageDecoder.decodeBitmap(source) { decoder, _, _ ->
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            decoder.setTargetSize(ImageFingerprint.HASH_WIDTH, ImageFingerprint.HASH_HEIGHT)
        }
        val w = ImageFingerprint.HASH_WIDTH
        val h = ImageFingerprint.HASH_HEIGHT
        val pixels = IntArray(w * h)
        try {
            bitmap.getPixels(pixels, 0, w, 0, 0, w, h)
        } finally {
            bitmap.recycle()
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
