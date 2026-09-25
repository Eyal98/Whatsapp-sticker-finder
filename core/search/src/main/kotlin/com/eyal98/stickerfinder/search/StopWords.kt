package com.eyal98.stickerfinder.search

/**
 * Words that carry no meaning for sticker search. Every query word must match, so a query like
 * "a cat that is sad" would otherwise find nothing: captions rarely contain "that" or "is".
 * Negations ("no", "not", "לא") are deliberately kept, since stickers often say them.
 */
object StopWords {

    private val WORDS: Set<String> = listOf(
        // English
        "a", "an", "the", "is", "are", "was", "be", "am", "to", "of", "in", "on", "at", "for",
        "with", "and", "or", "it", "its", "this", "that", "so", "just", "very", "some",
        "something", "sticker", "stickers", "when", "who", "which", "about",
        // Hebrew
        "של", "את", "עם", "על", "זה", "זאת", "זו", "הוא", "היא", "הם", "הן", "גם", "או", "אבל",
        "כי", "אם", "כמו", "רק", "עוד", "מאוד", "כש", "ש", "משהו", "מדבקה", "מדבקות", "כאשר",
    ).map { TextNormalizer.normalize(it) }.toSet()

    fun isStopWord(normalizedToken: String): Boolean = normalizedToken in WORDS
}
