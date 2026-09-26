package com.eyal98.stickerfinder.index

import android.util.Log
import com.eyal98.stickerfinder.data.EmbedderAccess
import com.eyal98.stickerfinder.data.StickerDao
import com.eyal98.stickerfinder.data.StickerVector
import com.eyal98.stickerfinder.search.EmbeddingText
import com.eyal98.stickerfinder.search.TextEmbedder
import com.eyal98.stickerfinder.search.Vectors
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

/**
 * Keeps every sticker's meaning vector in step with its text (captions, printed text, tags).
 * Only stickers whose text or model changed are embedded again.
 */
class EmbedIndexer(
    private val dao: StickerDao,
    private val embedders: EmbedderAccess,
) {

    private sealed interface Outcome {
        data object UpToDate : Outcome
        data object Failed : Outcome
        class Embedded(val model: String, val vector: FloatArray) : Outcome
    }

    /**
     * Brings vectors up to date until done or [budget] runs out. Returns null if no embedding
     * model is available.
     */
    suspend fun embedPending(budget: WorkBudget = WorkBudget()): StickerIndexer.Progress? {
        val states = dao.vectorStates().associateBy { it.stickerId }
        var written = 0
        val stale = mutableListOf<Long>()
        for (sticker in dao.allStickers()) {
            currentCoroutineContext().ensureActive()
            if (budget.exhausted) return StickerIndexer.Progress(written, finished = false)
            val text = EmbeddingText.document(
                sticker.captionEn, sticker.captionHe, sticker.captionTags, sticker.ocrText, sticker.userTags, sticker.imageTags,
                sticker.packName, sticker.emojiWords,
            )
            if (text == null) {
                if (sticker.id in states) stale += sticker.id
                continue
            }
            val fingerprint = EmbeddingText.fingerprint(text)
            val state = states[sticker.id]
            val outcome = embedders.withEmbedder { embedder ->
                when {
                    state != null && state.model == embedder.modelId && state.fingerprint == fingerprint ->
                        Outcome.UpToDate
                    else -> try {
                        Outcome.Embedded(embedder.modelId, Vectors.prepare(embedder.embed(text, TextEmbedder.Kind.DOCUMENT), embedder.dimensions))
                    } catch (e: RuntimeException) {
                        // Leave this sticker for the next run rather than stopping the whole pass.
                        Log.w(TAG, "Embedding failed", e)
                        Outcome.Failed
                    }
                }
            } ?: return null
            if (outcome is Outcome.Embedded) {
                dao.upsertVector(StickerVector(sticker.id, outcome.model, fingerprint, Vectors.encode(outcome.vector)))
                written++
            }
        }
        if (stale.isNotEmpty()) dao.deleteVectors(stale)
        return StickerIndexer.Progress(written, finished = true)
    }

    private companion object {
        const val TAG = "EmbedIndexer"
    }
}
