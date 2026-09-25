package com.eyal98.stickerfinder.embed

import android.content.Context
import com.eyal98.stickerfinder.ml.InstalledModel
import com.eyal98.stickerfinder.search.TextEmbedder
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.EmbeddingEngine
import com.google.ai.edge.litertlm.EmbeddingEngineConfig
import com.google.ai.edge.litertlm.EmbeddingOptions
import com.google.ai.edge.litertlm.InputData

/**
 * A single-file (.litertlm) embedding model, such as Granite multilingual R2, on the CPU through
 * LiteRT-LM's EmbeddingEngine: the tokenizer, pooling and normalization are inside the file.
 * Granite takes plain text for both queries and documents (no prefix), per its model card.
 */
class LiteRtLmTextEmbedder private constructor(
    private val engine: EmbeddingEngine,
    override val modelId: String,
) : TextEmbedder {

    /**
     * All 768 dimensions: Granite's Matryoshka truncation isn't verified on this build, and
     * 10,000 stickers × 768 floats is about 30 MB.
     */
    override val dimensions: Int = FULL_DIMENSIONS

    override fun embed(text: String, kind: TextEmbedder.Kind): FloatArray =
        engine.computeEmbedding(listOf(InputData.Text(text)), options).embedding

    override fun close() {
        engine.close()
    }

    companion object {
        private const val FULL_DIMENSIONS = 768

        /** Normalized, with the model's special tokens (the card says to keep the default). */
        private val options = EmbeddingOptions(normalize = true, insertSpecialTokens = true)

        /** Loads the model (a second or two). Call from a background thread. */
        fun create(context: Context, model: InstalledModel): LiteRtLmTextEmbedder {
            val engine = EmbeddingEngine(
                EmbeddingEngineConfig(
                    modelPath = model.file.absolutePath,
                    backend = Backend.CPU(),
                    cacheDir = context.cacheDir.absolutePath,
                ),
            )
            try {
                engine.initialize()
            } catch (e: Exception) {
                runCatching { engine.close() }
                throw e
            }
            return LiteRtLmTextEmbedder(engine, model.id)
        }
    }
}
