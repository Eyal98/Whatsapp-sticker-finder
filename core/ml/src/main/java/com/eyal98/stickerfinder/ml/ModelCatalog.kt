package com.eyal98.stickerfinder.ml

/**
 * A model file the app knows how to use. The app has no internet access, so the user downloads
 * it from [downloadPage] and imports it.
 *
 * [sha256] pins a known-good file. When it's null, or the imported file doesn't match any pinned
 * hash, the app shows the file's SHA-256 and asks the user to compare it with the one on the
 * download page (Hugging Face lists it on each file's page) before trusting it.
 */
data class ModelSpec(
    val id: String,
    val displayName: String,
    val fileName: String,
    val approxSize: String,
    val downloadPage: String,
    /** RAM the phone needs, in bytes. */
    val minRamBytes: Long,
    val sha256: String?,
)

/** Gemma models are covered by the Gemma Terms of Use. */
object ModelCatalog {

    /** Smaller and faster caption model; the default. */
    val GEMMA_3N_E2B = ModelSpec(
        id = "gemma-3n-e2b-it-int4",
        displayName = "Gemma 3n E2B",
        fileName = "gemma-3n-E2B-it-int4.task",
        approxSize = "3.1 GB",
        downloadPage = "https://huggingface.co/google/gemma-3n-E2B-it-litert-preview",
        // Phones sold as "6 GB" report a little less than that.
        minRamBytes = 5_500_000_000L,
        sha256 = null,
    )

    /** Better descriptions, slower, and needs more memory. */
    val GEMMA_3N_E4B = ModelSpec(
        id = "gemma-3n-e4b-it-int4",
        displayName = "Gemma 3n E4B",
        fileName = "gemma-3n-E4B-it-int4.task",
        approxSize = "4.4 GB",
        downloadPage = "https://huggingface.co/google/gemma-3n-E4B-it-litert-preview",
        minRamBytes = 7_500_000_000L,
        sha256 = null,
    )

    /** Multilingual (100+ languages, including Hebrew) text embedding model. */
    val EMBEDDING_GEMMA = ModelSpec(
        id = "embeddinggemma-300m-seq256",
        displayName = "EmbeddingGemma 300M",
        fileName = "embeddinggemma-300M_seq256_mixed-precision.tflite",
        approxSize = "180 MB",
        downloadPage = "https://huggingface.co/litert-community/embeddinggemma-300m",
        minRamBytes = 3_000_000_000L,
        sha256 = null,
    )

    /** The SentencePiece tokenizer that goes with [EMBEDDING_GEMMA], from the same page. */
    val EMBEDDING_GEMMA_TOKENIZER = ModelSpec(
        id = "embeddinggemma-sentencepiece",
        displayName = "EmbeddingGemma tokenizer",
        fileName = "sentencepiece.model",
        approxSize = "5 MB",
        downloadPage = "https://huggingface.co/litert-community/embeddinggemma-300m",
        minRamBytes = 0L,
        sha256 = null,
    )

    val CAPTION_MODELS = listOf(GEMMA_3N_E2B, GEMMA_3N_E4B)
    val EMBEDDING_MODELS = listOf(EMBEDDING_GEMMA)
    val TOKENIZERS = listOf(EMBEDDING_GEMMA_TOKENIZER)
}
