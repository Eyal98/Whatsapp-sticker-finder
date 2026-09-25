package com.eyal98.stickerfinder.ocr

/**
 * Filters raw OCR words down to text worth indexing. Sticker art (faces, outlines, emoji) makes
 * Tesseract report lots of low-confidence junk "words", which would pollute keyword search.
 */
object OcrTextCleaner {

    /** Tesseract word confidence is 0–100. To be tuned on the Phase 0 sample set. */
    const val MIN_WORD_CONFIDENCE = 60f

    private const val MIN_WORD_LENGTH = 2
    private const val MIN_LETTER_RATIO = 0.8

    data class Word(val text: String, val confidence: Float)

    /** A cleaned reading and how much to trust it, used to pick the best of several passes. */
    data class Reading(val text: String, val score: Float)

    fun clean(words: List<Word>): Reading? {
        val kept = words.mapNotNull { word ->
            val core = word.text.trim { !it.isLetterOrDigit() }
            core.takeIf { word.confidence >= MIN_WORD_CONFIDENCE && looksLikeAWord(it) }
                ?.let { it to word.confidence }
        }
        if (kept.isEmpty()) return null
        return Reading(
            text = kept.joinToString(" ") { it.first },
            score = kept.sumOf { it.second.toDouble() }.toFloat(),
        )
    }

    /** Picks the reading with more confidently recognized text. */
    fun best(vararg readings: Reading?): Reading? = readings.filterNotNull().maxByOrNull { it.score }

    private fun looksLikeAWord(core: String): Boolean {
        if (core.length < MIN_WORD_LENGTH) return false
        if (core.count { it.isLetterOrDigit() } < core.length * MIN_LETTER_RATIO) return false
        // Real words are in one script; "שa" or "Lש" is almost always noise from the artwork.
        val hebrew = core.any { it in 'א'..'ת' }
        val latin = core.any { it in 'a'..'z' || it in 'A'..'Z' }
        return !(hebrew && latin)
    }
}
