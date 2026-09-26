package com.eyal98.stickerfinder.caption

/**
 * The instruction sent with each sticker, and the parser for the model's reply. Small on-device
 * models often break JSON, so the reply is a few labelled lines, and parsing is lenient.
 */
object CaptionPrompt {

    private const val MAX_HINT_LENGTH = 200
    private const val MAX_FIELD_LENGTH = 300
    private const val MAX_TAGS = 30
    private const val MAX_TAG_LENGTH = 40

    /**
     * Changes when the prompt asks for something new; descriptions written with an older prompt
     * are then written again.
     */
    const val VERSION = 2

    fun build(printedText: String?, packName: String? = null): String = buildString {
        appendLine("You are labeling a chat sticker so that people can find it by searching for what they remember about it.")
        appendLine("Look at the image carefully and describe it in English and in Hebrew.")
        quoted(printedText)?.let { appendLine("Text read from the sticker by OCR (it may contain mistakes): $it") }
        quoted(packName)?.let { appendLine("It comes from a sticker pack named $it, which often names the show, character or theme.") }
        appendLine(
            "Be specific, not generic. If you recognize a cartoon, TV, movie, game or anime character, " +
                "a TV show, a movie, a meme or a brand, name it exactly (for example SpongeBob, Homer Simpson, " +
                "Pikachu, Kermit the Frog). Only name what you are sure about.",
        )
        appendLine("Reply in exactly this format and nothing else:")
        appendLine("EN: one short sentence: who or what is shown (by name if known), what they are doing, and the feeling or message")
        appendLine("HE: the same sentence in Hebrew")
        appendLine("NAMES: the characters, shows, movies, memes or brands shown, comma-separated, or none")
        append("TAGS: 10 to 20 comma-separated search keywords in English and Hebrew: names and their Hebrew spelling, ")
        append("the show, notable objects and clothing, actions, emotions, and situations when someone would send this sticker")
    }

    /** Quotes are replaced so the value can't close the quoted string early. */
    private fun quoted(value: String?): String? =
        value?.trim()?.take(MAX_HINT_LENGTH)?.takeIf { it.isNotEmpty() }?.let { "\"${it.replace('"', '\'')}\"" }

    private val LABELS = mapOf(
        "en" to Field.EN, "english" to Field.EN, "אנגלית" to Field.EN,
        "he" to Field.HE, "hebrew" to Field.HE, "עברית" to Field.HE,
        "tags" to Field.TAGS, "keywords" to Field.TAGS, "תגיות" to Field.TAGS,
        "names" to Field.NAMES, "name" to Field.NAMES, "שמות" to Field.NAMES,
    )

    private enum class Field { EN, HE, NAMES, TAGS }

    /** What the model writes in NAMES when it recognizes nothing. */
    private val NO_NAMES = setOf("none", "no", "n/a", "unknown", "אין", "ללא")

    // "EN:", "**EN**:", "- Hebrew -", "TAGS =" ...
    private val LINE = Regex("""^[\s*#>\-•]*([\p{L}]+)[\s*]*[:：=\-–]\s*(.*)$""")
    private val TAG_SEPARATORS = Regex("[,،;|\n]")

    // Reasoning some models write before the answer ("<think>…</think>", Gemma's thought channel).
    private val THINKING = Regex("""<think>.*?</think>|<\|channel>.*?<channel\|>""", RegexOption.DOT_MATCHES_ALL)

    fun parse(reply: String): StickerCaption? {
        val values = mutableMapOf<Field, String>()
        for (raw in reply.replace(THINKING, "").lines()) {
            val match = LINE.find(raw.trim()) ?: continue
            val field = LABELS[match.groupValues[1].lowercase()] ?: continue
            val value = clean(match.groupValues[2])
            if (value.isNotEmpty() && field !in values) values[field] = value
        }
        // Names first: they're the most specific keywords, and tags are capped.
        val names = values[Field.NAMES].orEmpty().split(TAG_SEPARATORS).map { clean(it) }
            .filter { it.lowercase() !in NO_NAMES }
        val tags = (names + values[Field.TAGS].orEmpty().split(TAG_SEPARATORS))
            .map { clean(it).removePrefix("#") }
            .filter { it.isNotEmpty() && it.length <= MAX_TAG_LENGTH && it.lowercase() !in NO_NAMES }
            .distinct()
            .take(MAX_TAGS)
        val caption = StickerCaption(
            english = values[Field.EN]?.take(MAX_FIELD_LENGTH),
            hebrew = values[Field.HE]?.take(MAX_FIELD_LENGTH),
            tags = tags,
        )
        return caption.takeIf { it.english != null || it.hebrew != null || it.tags.isNotEmpty() }
    }

    /** Strips markdown emphasis, surrounding quotes and whitespace. */
    private fun clean(value: String): String =
        value.replace("**", "").trim().trim('"', '\'', '“', '”', '„', '*', '.', ' ')
}
