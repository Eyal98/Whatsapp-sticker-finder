package com.eyal98.stickerfinder.search

/**
 * A lightweight heuristic for Hebrew one-letter prefixes (ו, ה, ב, ל, מ, ש, כ). Instead of full
 * morphological analysis, it generates the token with up to [MAX_PREFIX_LETTERS] leading prefix
 * letters removed, e.g. "והחתול" → ["והחתול", "החתול", "חתול"].
 *
 * The variants only widen recall. Semantic search in Phase 2 handles the harder cases.
 */
object HebrewPrefixes {

    // Final forms are already mapped to regular letters by TextNormalizer, so כ covers ך too.
    private val PREFIX_LETTERS = setOf('ו', 'ה', 'ב', 'ל', 'מ', 'ש', 'כ')
    private const val MAX_PREFIX_LETTERS = 3
    private const val MIN_STEM_LENGTH = 2

    fun variants(token: String): List<String> {
        if (token.isEmpty() || !TextNormalizer.isHebrewLetter(token[0])) return listOf(token)
        val result = mutableListOf(token)
        var i = 0
        while (i < MAX_PREFIX_LETTERS &&
            token.length - (i + 1) >= MIN_STEM_LENGTH &&
            token[i] in PREFIX_LETTERS
        ) {
            i++
            result += token.substring(i)
        }
        return result
    }
}
