package com.eyal98.stickerfinder.index

import android.util.Log
import com.eyal98.stickerfinder.data.EmbedderAccess
import com.eyal98.stickerfinder.data.StickerDao
import com.eyal98.stickerfinder.data.StickerVector
import com.eyal98.stickerfinder.search.EmbeddingText
import com.eyal98.stickerfinder.search.Facet
import com.eyal98.stickerfinder.search.TextEmbedder
import com.eyal98.stickerfinder.search.Vectors
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

/**
 * Keeps every sticker's meaning vectors in step with its text (printed text; tags, descriptions).
 * Only stickers whose text or model changed are embedded again.
 */
class EmbedIndexer(
    private val dao: StickerDao,
    private val embedders: EmbedderAccess,
) {

    private sealed interface Outcome {
        data object UpToDate : Outcome
        data object Failed : Outcome
        class Embedded(val vector: StickerVector) : Outcome
    }

    /**
     * Brings vectors up to date until done or [budget] runs out. Returns null if no embedding
     * model is available. Each sticker gets a vector per [Facet] it has text for (what it says,
     * what it shows); vectors of facets it no longer has, like an older build's combined one, are
     * removed once the new ones are saved.
     */
    suspend fun embedPending(budget: WorkBudget = WorkBudget()): StickerIndexer.Progress? {
        val states = dao.vectorStates().groupBy { it.stickerId }
        var written = 0
        for (sticker in dao.allStickers()) {
            currentCoroutineContext().ensureActive()
            if (budget.exhausted) return StickerIndexer.Progress(written, finished = false)
            val facets = EmbeddingText.facets(
                sticker.captionEn, sticker.captionHe, sticker.captionTags, sticker.ocrText, sticker.userTags,
                sticker.visibleImageTags, sticker.emojiWords, sticker.peopleNames, sticker.userDescription,
            )
            val existing = states[sticker.id].orEmpty().associateBy { it.facet }
            var failed = false
            for ((facet, text) in facets) {
                val fingerprint = EmbeddingText.fingerprint(text)
                val state = existing[facet.code]
                val outcome = embedders.withEmbedder { embedder ->
                    when {
                        state != null && state.model == embedder.modelId && state.fingerprint == fingerprint -> Outcome.UpToDate
                        else -> try {
                            Outcome.Embedded(
                                StickerVector(
                                    sticker.id, facet.code, embedder.modelId, fingerprint,
                                    Vectors.encode(Vectors.prepare(embedder.embed(text, TextEmbedder.Kind.DOCUMENT), embedder.dimensions)),
                                ),
                            )
                        } catch (e: RuntimeException) {
                            // Leave this sticker for the next run rather than stopping the whole pass.
                            Log.w(TAG, "Embedding failed", e)
                            Outcome.Failed
                        }
                    }
                } ?: return null
                when (outcome) {
                    is Outcome.Embedded -> {
                        dao.upsertVector(outcome.vector)
                        written++
                    }
                    Outcome.Failed -> failed = true
                    Outcome.UpToDate -> Unit
                }
            }
            // Only once its new vectors are saved, so search never loses the sticker in between.
            if (!failed) {
                val current = facets.keys.map { it.code }.toSet()
                for (facet in existing.keys - current) dao.deleteVector(sticker.id, facet)
            }
        }
        return StickerIndexer.Progress(written, finished = true)
    }

    private companion object {
        const val TAG = "EmbedIndexer"
    }
}
