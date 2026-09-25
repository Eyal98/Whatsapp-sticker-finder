package com.eyal98.stickerfinder.caption

/**
 * Caption models the app knows how to run. The app has no internet access, so the user downloads
 * the file from [downloadPage] and imports it.
 *
 * [sha256] pins a known-good file. When it's null, or the imported file doesn't match any pinned
 * hash, the app shows the file's SHA-256 and asks the user to compare it with the one on the
 * download page (Hugging Face lists it on each file's page) before trusting it.
 */
data class CaptionModel(
    val id: String,
    val displayName: String,
    val fileName: String,
    val approxSize: String,
    val downloadPage: String,
    /** RAM the phone needs, in bytes. */
    val minRamBytes: Long,
    val sha256: String?,
)

object ModelCatalog {

    private const val GB = 1_000_000_000L

    /** Smaller and faster; the default. Gemma models are covered by the Gemma Terms of Use. */
    val GEMMA_3N_E2B = CaptionModel(
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
    val GEMMA_3N_E4B = CaptionModel(
        id = "gemma-3n-e4b-it-int4",
        displayName = "Gemma 3n E4B",
        fileName = "gemma-3n-E4B-it-int4.task",
        approxSize = "4.4 GB",
        downloadPage = "https://huggingface.co/google/gemma-3n-E4B-it-litert-preview",
        minRamBytes = 7_500_000_000L,
        sha256 = null,
    )

    val ALL = listOf(GEMMA_3N_E2B, GEMMA_3N_E4B)

    val RECOMMENDED = GEMMA_3N_E2B

    fun byHash(sha256: String): CaptionModel? = ALL.firstOrNull { it.sha256 == sha256 }

    fun byFileName(name: String): CaptionModel? = ALL.firstOrNull { it.fileName.equals(name, ignoreCase = true) }

    fun byId(id: String): CaptionModel? = ALL.firstOrNull { it.id == id }
}
