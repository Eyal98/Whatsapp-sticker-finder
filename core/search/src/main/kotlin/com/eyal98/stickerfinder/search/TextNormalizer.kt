package com.eyal98.stickerfinder.search

import java.text.Normalizer

/**
 * Normalizes Hebrew and English text so that indexed text and user queries compare equal
 * regardless of niqqud, final letter forms, geresh/gershayim, case, or repeated letters.
 */
object TextNormalizer {

    private val FINAL_LETTERS = mapOf(
        'ך' to 'כ',
        'ם' to 'מ',
        'ן' to 'נ',
        'ף' to 'פ',
        'ץ' to 'צ',
    )

    /** Hebrew punctuation in the U+0591–U+05C7 block that separates words rather than marking vowels. */
    private val HEBREW_PUNCTUATION = setOf('־', '׀', '׃', '׆')

    /** Characters removed inside a word, so that e.g. מזל"ט and מזל״ט both become מזלט. */
    private val IN_WORD_QUOTES = setOf('\'', '"', '׳', '״', '’', '‘', '”', '“')

    private val TOKEN_SEPARATOR = Regex("[^\\p{L}\\p{N}]+")
    private val REPEATED_CHAR = Regex("(.)\\1{3,}")

    fun isHebrewLetter(c: Char): Boolean = c in 'א'..'ת'

    private fun isNiqqudOrCantillation(c: Char): Boolean =
        c in '֑'..'ׇ' && c !in HEBREW_PUNCTUATION

    /** Returns the normalized text with words separated by single spaces. */
    fun normalize(text: String): String = tokenize(text).joinToString(" ")

    /** Splits text into normalized tokens. */
    fun tokenize(text: String): List<String> {
        val nfkc = Normalizer.normalize(text, Normalizer.Form.NFKC)
        val sb = StringBuilder(nfkc.length)
        for (c in nfkc) {
            when {
                isNiqqudOrCantillation(c) -> Unit
                c in HEBREW_PUNCTUATION -> sb.append(' ')
                c in IN_WORD_QUOTES -> Unit
                else -> sb.append(FINAL_LETTERS[c] ?: c.lowercaseChar())
            }
        }
        return sb.split(TOKEN_SEPARATOR)
            .filter { it.isNotEmpty() }
            // "חחחחחח" and "חחחח" both become "חחח"
            .map { REPEATED_CHAR.replace(it) { m -> m.groupValues[1].repeat(3) } }
    }
}
