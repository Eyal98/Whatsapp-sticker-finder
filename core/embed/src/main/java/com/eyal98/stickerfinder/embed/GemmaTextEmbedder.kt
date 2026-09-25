package com.eyal98.stickerfinder.embed

import com.eyal98.stickerfinder.ml.InstalledModel
import com.eyal98.stickerfinder.search.TextEmbedder
import com.google.ai.edge.localagents.rag.models.EmbedData
import com.google.ai.edge.localagents.rag.models.EmbeddingRequest
import com.google.ai.edge.localagents.rag.models.GemmaEmbeddingModel

/**
 * EmbeddingGemma on the phone's CPU through the AI Edge RAG SDK. EmbeddingGemma expects different
 * prompts for search queries and for the documents being searched; the SDK picks them from the
 * task type and the query flag, so both are always set.
 */
class GemmaTextEmbedder private constructor(
    private val model: GemmaEmbeddingModel,
    override val modelId: String,
) : TextEmbedder {

    override fun embed(text: String, kind: TextEmbedder.Kind): FloatArray {
        val task = when (kind) {
            TextEmbedder.Kind.QUERY -> EmbedData.TaskType.RETRIEVAL_QUERY
            TextEmbedder.Kind.DOCUMENT -> EmbedData.TaskType.RETRIEVAL_DOCUMENT
        }
        val isQuery = kind == TextEmbedder.Kind.QUERY
        val request = EmbeddingRequest.create(listOf(EmbedData.create(text, task, isQuery)))
        return model.getEmbeddings(request).get().toFloatArray()
    }

    override fun close() {
        // The SDK model has no explicit release; dropping the reference frees it.
    }

    companion object {
        fun create(model: InstalledModel, tokenizer: InstalledModel): GemmaTextEmbedder =
            GemmaTextEmbedder(
                GemmaEmbeddingModel(model.file.absolutePath, tokenizer.file.absolutePath, false),
                model.id,
            )
    }
}
