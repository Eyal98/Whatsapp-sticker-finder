package com.eyal98.stickerfinder.embed

import android.content.Context
import android.util.Log
import com.eyal98.stickerfinder.data.EmbedderAccess
import com.eyal98.stickerfinder.ml.DeviceCapability
import com.eyal98.stickerfinder.ml.ModelCatalog
import com.eyal98.stickerfinder.ml.ModelCrashGuard
import com.eyal98.stickerfinder.ml.ModelStore
import com.eyal98.stickerfinder.search.TextEmbedder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * One embedding model per process, shared by search and the background indexer. Loaded on first
 * use (about a second), reloaded if the user installs different files, and released with
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

    /** True when both files are installed, not turned off after a crash, and memory suffices. */
    fun isAvailable(): Boolean {
        if (ModelCrashGuard.isDisabled(appContext, ModelCrashGuard.EMBEDDING)) return false
        val model = ModelStore.EMBEDDING.installed(appContext) ?: return false
        ModelStore.EMBEDDING_TOKENIZER.installed(appContext) ?: return false
        return DeviceCapability.canRun(appContext, model.model, ModelCatalog.EMBEDDING_GEMMA)
    }

    suspend fun release() = lock.withLock {
        loaded?.second?.close()
        loaded = null
        ModelCrashGuard.clearBusy(appContext, ModelCrashGuard.EMBEDDING)
    }

    private fun current(): TextEmbedder? {
        if (!isAvailable()) return null
        val model = ModelStore.EMBEDDING.installed(appContext) ?: return null
        val tokenizer = ModelStore.EMBEDDING_TOKENIZER.installed(appContext) ?: return null
        val key = model.sha256 + tokenizer.sha256
        loaded?.let { (k, embedder) -> if (k == key) return embedder }
        loaded?.second?.close()
        loaded = null
        // Busy while the model is loaded; a native crash meanwhile turns it off next start.
        ModelCrashGuard.markBusy(appContext, ModelCrashGuard.EMBEDDING)
        return try {
            GemmaTextEmbedder.create(model, tokenizer).also { loaded = key to it }
        } catch (e: RuntimeException) {
            Log.w(TAG, "Could not load the embedding model", e)
            ModelCrashGuard.disable(appContext, ModelCrashGuard.EMBEDDING, ModelCrashGuard.describe(e))
            null
        }
    }

    private companion object {
        const val TAG = "EmbedderHolder"
    }
}
