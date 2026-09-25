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

    fun build(printedText: String?): String = buildString {
        appendLine("You are labeling a chat sticker so that people can find it by searching.")
        appendLine("Look at the image and describe it in English and in Hebrew.")
        val hint = printedText?.trim()?.take(MAX_HINT_LENGTH)
        if (!hint.isNullOrEmpty()) {
            // Quotes are replaced so the hint can't close the quoted string early.
            appendLine("Text read from the sticker by OCR (it may contain mistakes): \"${hint.replace('"', '\'')}\"")
        }
        appendLine("Reply in exactly this format and nothing else:")
        appendLine("EN: one short sentence saying who or what is shown, what they do, and the feeling or message")
        appendLine("HE: the same sentence in Hebrew")
        append("TAGS: 8 to 15 comma-separated search keywords in English and Hebrew: characters, ")
        append("objects, emotions, and situations when someone would send this sticker")
    }

    private val LABELS = mapOf(
        "en" to Field.EN, "english" to Field.EN, "אנגלית" to Field.EN,
        "he" to Field.HE, "hebrew" to Field.HE, "עברית" to Field.HE,
        "tags" to Field.TAGS, "keywords" to Field.TAGS, "תגיות" to Field.TAGS,
    )

    private enum class Field { EN, HE, TAGS }

    // "EN:", "**EN**:", "- Hebrew -", "TAGS =" ...
    private val LINE = Regex("""^[\s*#>\-•]*([\p{L}]+)[\s*]*[:：=\-–]\s*(.*)$""")
    private val TAG_SEPARATORS = Regex("[,،;|\n]")

    fun parse(reply: String): StickerCaption? {
        val values = mutableMapOf<Field, String>()
        for (raw in reply.lines()) {
            val match = LINE.find(raw.trim()) ?: continue
            val field = LABELS[match.groupValues[1].lowercase()] ?: continue
            val value = clean(match.groupValues[2])
            if (value.isNotEmpty() && field !in values) values[field] = value
        }
        val tags = values[Field.TAGS].orEmpty()
            .split(TAG_SEPARATORS)
            .map { clean(it).removePrefix("#") }
            .filter { it.isNotEmpty() && it.length <= MAX_TAG_LENGTH }
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
