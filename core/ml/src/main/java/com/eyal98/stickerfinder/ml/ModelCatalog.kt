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

/** Pinned by the SigLIP labels workflow (it prints the model's hash). */
private const val SIGLIP2_SHA256 = "a30ebb7b3ee15eaa68a18f9ab6a2ed740c15c343d25d898dc482317473320854"

/** Gemma models are covered by the Gemma Terms of Use. */
object ModelCatalog {

    /**
     * The default caption model: Gemma 4 E2B, open to download (no approval), reads images, and
     * runs on LiteRT-LM. The generic file, not the web one (text only) or chip-specific builds.
     */
    val GEMMA_4_E2B = ModelSpec(
        id = "gemma-4-e2b-it-litertlm",
        displayName = "Gemma 4 E2B",
        fileName = "gemma-4-E2B-it.litertlm",
        approxSize = "2.6 GB",
        downloadPage = "https://huggingface.co/litert-community/gemma-4-E2B-it-litert-lm",
        minRamBytes = 5_500_000_000L,
        sha256 = "181938105e0eefd105961417e8da75903eacda102c4fce9ce90f50b97139a63c",
    )

    /** Better descriptions than E2B, slower, and needs more memory. */
    val GEMMA_4_E4B = ModelSpec(
        id = "gemma-4-e4b-it-litertlm",
        displayName = "Gemma 4 E4B",
        fileName = "gemma-4-E4B-it.litertlm",
        approxSize = "3.7 GB",
        downloadPage = "https://huggingface.co/litert-community/gemma-4-E4B-it-litert-lm",
        minRamBytes = 7_500_000_000L,
        sha256 = "0b2a8980ce155fd97673d8e820b4d29d9c7d99b8fa6806f425d969b145bd52e0",
    )

    /**
     * Gemma 3n, the earlier models. Google approves downloads of these by hand, so they aren't
     * recommended; a file already downloaded still works.
     */
    val GEMMA_3N_E2B = ModelSpec(
        id = "gemma-3n-e2b-it-int4-litertlm",
        displayName = "Gemma 3n E2B",
        fileName = "gemma-3n-E2B-it-int4.litertlm",
        approxSize = "3.7 GB",
        downloadPage = "https://huggingface.co/google/gemma-3n-E2B-it-litert-lm",
        // Phones sold as "6 GB" report a little less than that.
        minRamBytes = 5_500_000_000L,
        sha256 = null,
    )

    /** Better descriptions, slower, and needs more memory. */
    val GEMMA_3N_E4B = ModelSpec(
        id = "gemma-3n-e4b-it-int4-litertlm",
        displayName = "Gemma 3n E4B",
        fileName = "gemma-3n-E4B-it-int4.litertlm",
        approxSize = "4.9 GB",
        downloadPage = "https://huggingface.co/google/gemma-3n-E4B-it-litert-lm",
        minRamBytes = 7_500_000_000L,
        sha256 = null,
    )

    /**
     * The default embedding model: IBM Granite multilingual R2 (Hebrew is among its 52
     * enhanced-support languages), as one LiteRT-LM file with the tokenizer inside, open to
     * download. The int8 build, meant for phones.
     */
    val GRANITE_EMBEDDING = ModelSpec(
        id = "granite-embedding-311m-r2-wi8fc",
        displayName = "Granite multilingual embedding",
        fileName = "granite-embedding-311m-r2_wi8fc.litertlm",
        approxSize = "332 MB",
        downloadPage = "https://huggingface.co/litert-community/granite-embedding-311m-multilingual-r2",
        minRamBytes = 3_000_000_000L,
        sha256 = "beb2be205abc766a670522e651be5713cecb4e4c33e5ef5c30f6a710d4226db5",
    )

    /**
     * SigLIP 2 image tower (ViT-B/16, 224 px) for picture tags. The app's label vectors were made
     * with the matching text tower, so only this exact file works: its hash is pinned.
     */
    val SIGLIP2_B16 = ModelSpec(
        id = "siglip2-base-patch16-224-fp16",
        displayName = "SigLIP 2 (image)",
        fileName = "siglip2_base_224_fp16.tflite",
        approxSize = "185 MB",
        downloadPage = "https://huggingface.co/litert-community/SigLIP2-base-patch16-224",
        minRamBytes = 3_000_000_000L,
        sha256 = SIGLIP2_SHA256,
    )

    val CAPTION_MODELS = listOf(GEMMA_4_E2B, GEMMA_4_E4B, GEMMA_3N_E2B, GEMMA_3N_E4B)
    val EMBEDDING_MODELS = listOf(GRANITE_EMBEDDING)
    val IMAGE_MODELS = listOf(SIGLIP2_B16)
}
