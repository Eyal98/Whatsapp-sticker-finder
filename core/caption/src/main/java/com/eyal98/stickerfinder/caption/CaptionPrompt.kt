package com.eyal98.stickerfinder.caption

/**
 * The instruction sent with each sticker, and the parser for the model's reply. Small on-device
 * models often break JSON, so the reply is a few labelled lines, and parsing is lenient.
 */
object CaptionPrompt {

    private const val MAX_HINT_LENGTH = 200
    private const val MAX_FIELD_LENGTH = 300
    private const val MAX_TAGS = 15
    private const val MAX_TAG_LENGTH = 40

    /**
     * Changes when the prompt asks for something new; descriptions written with an older prompt
     * are then written again.
     */
    const val VERSION = 3

    fun build(printedText: String?, packName: String? = null): String = buildString {
        appendLine("You are labeling a chat sticker so that people can find it by searching for what they remember about it.")
        appendLine("Look at the picture carefully and describe what it shows, in English and in Hebrew.")
        quoted(printedText)?.let {
            // The model misreads Hebrew lettering and then "translates" its own mistake; the OCR
            // text is searchable already, so it's only context here.
            appendLine("The words written on the sticker are $it. They are already searchable: use them only to understand the message. Do not quote, transcribe or translate them.")
        }
        quoted(packName)?.let {
            appendLine("It comes from a sticker pack named $it, which may name the show or character. The pack name is already searchable: do not repeat it.")
        }
        appendLine(
            "Be specific, not generic. If you recognize a cartoon, TV, movie, game or anime character, " +
                "a TV show, a movie, a meme or a brand, name it exactly (for example SpongeBob, Homer Simpson, " +
                "Pikachu, Kermit the Frog). Only name what you are sure about.",
        )
        appendLine("State what you see plainly. Don't hedge with words like likely, possibly, perhaps, seems or suggesting.")
        appendLine("Reply in exactly this format and nothing else:")
        appendLine("EN: one short sentence: who or what is shown (by name if known), what they are doing, and the feeling or message")
        appendLine("HE: the same sentence in simple, correct Hebrew")
        appendLine("NAMES: the characters, shows, movies, memes or brands shown, comma-separated, or none")
        append("TAGS: 8 to 12 comma-separated keywords, in English and Hebrew, that tell this sticker apart from others: ")
        append("names and their Hebrew spelling, notable objects and clothing, the action, the emotion, and when someone would send it. ")
        append("No generic words like sticker, meme, funny, reaction, character, cartoon or emotion, and no repeats.")
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

    /**
     * Keywords that fit any sticker: they only add noise to search. Any keyword containing
     * "sticker" is dropped too ("yes sticker pack", "reaction sticker").
     */
    private val GENERIC_TAGS = setOf(
        "meme", "memes", "funny", "reaction", "character", "cartoon", "emotion", "feeling", "expression",
        "humor", "humour", "image", "picture", "illustration", "graphic", "text", "hebrew text", "text overlay",
        "close-up", "close up", "cute", "cool", "vibes", "anime meme", "manga meme", "reaction meme", "funny meme",
        "character art", "social media", "relatable", "מדבקה", "מם", "מצחיק", "דמות",
    )

    private fun isGeneric(tag: String): Boolean {
        val t = tag.lowercase()
        return t in GENERIC_TAGS || "sticker" in t
    }

    /**
     * Reads the model's reply. Keywords matching [exclude] (the pack name, already searchable)
     * are dropped, as are generic ones and repeats that differ only in case.
     */
    fun parse(reply: String, exclude: Collection<String> = emptyList()): StickerCaption? {
        val excluded = exclude.map { it.trim().lowercase() }.toSet()
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
            .filterNot { isGeneric(it) || it.lowercase() in excluded }
            .distinctBy { it.lowercase() }
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
