package com.eyal98.stickerfinder.embed

import android.content.Context
import android.util.Log
import com.eyal98.stickerfinder.data.EmbedderAccess
import com.eyal98.stickerfinder.ml.BundledEmbedding
import com.eyal98.stickerfinder.ml.DeviceCapability
import com.eyal98.stickerfinder.ml.ModelCatalog
import com.eyal98.stickerfinder.ml.ModelCrashGuard
import com.eyal98.stickerfinder.search.TextEmbedder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * One embedding model per process, shared by search and the background indexer. Loaded on first
 * use (about a second), reloaded if the user installs a different file, and released with
 * [release] when the app goes to the background.
 */
class EmbedderHolder(context: Context) : EmbedderAccess {

    private val appContext = context.applicationContext
    private val lock = Mutex()
    private var loaded: Pair<String, TextEmbedder>? = null

    override suspend fun <T> withEmbedder(block: (TextEmbedder) -> T): T? = lock.withLock {
        withContext(Dispatchers.Default) {
            current()?.let(block)
        }
    }

    /** True when the model is installed, it wasn't turned off after a crash, and memory suffices. */
    fun isAvailable(): Boolean {
        if (ModelCrashGuard.isDisabled(appContext, ModelCrashGuard.EMBEDDING)) return false
        val model = BundledEmbedding.active(appContext) ?: return false
        return DeviceCapability.canRun(appContext, model.model, ModelCatalog.GRANITE_EMBEDDING)
    }

    suspend fun release() = lock.withLock {
        loaded?.second?.close()
        loaded = null
        ModelCrashGuard.clearBusy(appContext, ModelCrashGuard.EMBEDDING)
    }

    private fun current(): TextEmbedder? {
        if (!isAvailable()) return null
        val model = BundledEmbedding.active(appContext) ?: return null
        if (!model.isLiteRtLm) {
            // EmbeddingGemma (.tflite) ran on the RAG SDK, dropped to cut the app's size by more
            // than half. One installed before then is reported, not opened.
            return loadFailed(
                IllegalStateException("EmbeddingGemma (.tflite) is no longer supported: remove it and import the Granite .litertlm file"),
            )
        }
        val key = model.sha256
        loaded?.let { (k, embedder) -> if (k == key) return embedder }
        loaded?.second?.close()
        loaded = null
        // Busy while the model is loaded; a native crash meanwhile turns it off next start.
        ModelCrashGuard.markBusy(appContext, ModelCrashGuard.EMBEDDING)
        return try {
            LiteRtLmTextEmbedder.create(appContext, model).also { loaded = key to it }
        } catch (e: Exception) {
            loadFailed(e)
        } catch (e: LinkageError) {
            // A runtime's native library that doesn't load on this phone.
            loadFailed(e)
        }
    }

    private fun loadFailed(e: Throwable): TextEmbedder? {
        Log.w(TAG, "Could not load the embedding model", e)
        ModelCrashGuard.disable(appContext, ModelCrashGuard.EMBEDDING, ModelCrashGuard.describe(e))
        return null
    }

    private companion object {
        const val TAG = "EmbedderHolder"
    }
}
