package com.eyal98.stickerfinder.embed

import com.eyal98.stickerfinder.ml.InstalledModel
import com.eyal98.stickerfinder.search.TextEmbedder
import com.google.ai.edge.localagents.rag.models.EmbedData
import com.google.ai.edge.localagents.rag.models.EmbeddingRequest
import com.google.ai.edge.localagents.rag.models.GemmaEmbeddingModel

/**
 * EmbeddingGemma on the phone's CPU through the AI Edge RAG SDK. The SDK applies EmbeddingGemma's
 * query/document prompts according to the task type.
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
        val request = EmbeddingRequest.create(listOf(EmbedData.create(text, task)))
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
