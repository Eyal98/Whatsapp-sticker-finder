package com.eyal98.stickerfinder.search

/** Which side of a sticker a meaning vector describes (see [EmbeddingText.facets]). */
enum class Facet(val code: Int) {
    /** Older builds' single vector for everything; replaced as stickers are embedded again. */
    COMBINED(0),
    SAYS(1),
    SHOWS(2),
}

/** Builds the text that represents a sticker for semantic search, and a fingerprint of it. */
object EmbeddingText {

    /** Keeps each text well inside the model's 256-token window. */
    private const val MAX_LENGTH = 600

    /**
     * What a sticker is, split in two, each embedded on its own. One vector for everything let a
     * sticker's clear printed text drown in tags and noise (and the other way round), and the pack
     * name in every vector pulled whole packs together. Now a sticker matches a search by whichever
     * side is closer, and the pack name is left to keyword search.
     *
     * - [Facet.SAYS]: the words printed on it.
     * - [Facet.SHOWS]: what it shows and means: picture tags, the user's tags, description and
     *   people's names, emoji words, old descriptions.
     */
    fun facets(
        captionEn: String?,
        captionHe: String?,
        captionTags: String?,
        ocrText: String?,
        userTags: String?,
        imageTags: String?,
        emojiWords: String?,
        peopleNames: String?,
        userDescription: String?,
    ): Map<Facet, String> = buildMap {
        ocrText?.trim()?.takeIf { text -> text.count { it.isLetter() } >= MIN_PRINTED_LETTERS }
            ?.let { put(Facet.SAYS, it.take(MAX_LENGTH)) }
        val shows = buildList {
            userDescription?.takeIf { it.isNotBlank() }?.let { add(it.trim()) }
            peopleNames?.takeIf { it.isNotBlank() }?.let { add(it.trim()) }
            captionEn?.takeIf { it.isNotBlank() }?.let { add(it.trim()) }
            captionHe?.takeIf { it.isNotBlank() }?.let { add(it.trim()) }
            val tags = listOfNotNull(userTags, imageTags, emojiWords, captionTags).filter { it.isNotBlank() }
                .joinToString(", ") { it.trim() }.trim(',', ' ')
            if (tags.isNotEmpty()) add(tags)
        }
        if (shows.isNotEmpty()) put(Facet.SHOWS, shows.joinToString(". ").take(MAX_LENGTH))
    }

    /** Printed text with fewer letters than this is OCR noise, not words. */
    private const val MIN_PRINTED_LETTERS = 3

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
