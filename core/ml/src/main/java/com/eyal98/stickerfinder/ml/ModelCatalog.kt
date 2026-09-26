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

object ModelCatalog {

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
     * SigLIP 2 image tower (ViT-B/16, 224 px) for picture tags, bundled in the app (see
     * core/vision/siglip.properties). The label vectors were made with the matching text tower,
     * so only this exact file works: its hash is pinned. The id is stored with each sticker's
     * picture vector.
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

    val EMBEDDING_MODELS = listOf(GRANITE_EMBEDDING)
}
