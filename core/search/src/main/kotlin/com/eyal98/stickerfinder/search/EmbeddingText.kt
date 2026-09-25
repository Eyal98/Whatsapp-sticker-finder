package com.eyal98.stickerfinder.search

/** Builds the text that represents a sticker for semantic search, and a fingerprint of it. */
object EmbeddingText {

    /** Keeps the input well inside the model's 256-token window. */
    private const val MAX_LENGTH = 600

    /**
     * Returns null when there's nothing to embed. Fields are labelled so the model can tell a
     * description from words printed on the sticker.
     */
    fun document(
        captionEn: String?,
        captionHe: String?,
        captionTags: String?,
        ocrText: String?,
        userTags: String?,
    ): String? {
        val parts = buildList {
            captionEn?.takeIf { it.isNotBlank() }?.let { add(it.trim()) }
            captionHe?.takeIf { it.isNotBlank() }?.let { add(it.trim()) }
            ocrText?.takeIf { it.isNotBlank() }?.let { add("Text: ${it.trim()}") }
            val tags = listOfNotNull(captionTags, userTags).joinToString(", ") { it.trim() }.trim(',', ' ')
            if (tags.isNotEmpty()) add("Tags: $tags")
        }
        return parts.takeIf { it.isNotEmpty() }?.joinToString("\n")?.take(MAX_LENGTH)
    }

    /** 64-bit FNV-1a of the text; stored with each vector to notice when the text changed. */
    fun fingerprint(text: String): Long {
        var hash = -0x340d631b7bdddcdbL // FNV offset basis 0xcbf29ce484222325
        for (c in text) {
            hash = hash xor c.code.toLong()
            hash *= 0x100000001b3L
        }
        return hash
    }
}
