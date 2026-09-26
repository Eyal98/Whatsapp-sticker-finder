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
        /** Picture tags from the image model; added last so older fingerprints stay valid without them. */
        imageTags: String? = null,
        /** The sticker pack's name, and words for its emojis; also optional, for the same reason. */
        packName: String? = null,
        emojiWords: String? = null,
        /** Names the user gave the people on the sticker. */
        peopleNames: String? = null,
        /** The user's own description. */
        userDescription: String? = null,
    ): String? {
        val parts = buildList {
            captionEn?.takeIf { it.isNotBlank() }?.let { add(it.trim()) }
            captionHe?.takeIf { it.isNotBlank() }?.let { add(it.trim()) }
            ocrText?.takeIf { it.isNotBlank() }?.let { add("Text: ${it.trim()}") }
            packName?.takeIf { it.isNotBlank() }?.let { add("Sticker pack: ${it.trim()}") }
            peopleNames?.takeIf { it.isNotBlank() }?.let { add("People: ${it.trim()}") }
            userDescription?.takeIf { it.isNotBlank() }?.let { add(it.trim()) }
            val tags = listOfNotNull(captionTags, userTags, imageTags, emojiWords).filter { it.isNotBlank() }
                .joinToString(", ") { it.trim() }.trim(',', ' ')
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
