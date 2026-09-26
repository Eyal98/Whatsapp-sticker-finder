package com.eyal98.stickerfinder.index

/**
 * The sticker-pack details WhatsApp stickers carry inside the file: a JSON note in the WebP's EXIF
 * chunk with the pack name, its publisher and the emojis the sticker was filed under. Pack names
 * are often exactly what people search for ("Friends", "SpongeBob", a show or a meme).
 */
data class StickerMetadata(
    val packName: String?,
    val publisher: String?,
    /** Search words for the emojis, in English and Hebrew. */
    val emojiWords: List<String>,
) {
    companion object {
        private const val MAX_FIELD = 120
        private const val MAX_JSON = 16 * 1024

        /** Reads the metadata from a WebP file's bytes; null when there is none. */
        fun read(webp: ByteArray): StickerMetadata? {
            val json = exifJson(webp) ?: return null
            val name = field(json, "sticker-pack-name")
            val publisher = field(json, "sticker-pack-publisher")
            val words = emojis(json).flatMap { EmojiWords.of(it) }.distinct()
            if (name == null && publisher == null && words.isEmpty()) return null
            return StickerMetadata(name, publisher, words)
        }

        /** The JSON text inside the RIFF "EXIF" chunk, if any. */
        private fun exifJson(b: ByteArray): String? {
            if (b.size < 12 || !b.matches(0, "RIFF") || !b.matches(8, "WEBP")) return null
            var pos = 12
            while (pos + 8 <= b.size) {
                val size = b.le32(pos + 4)
                if (size < 0) return null
                val start = pos + 8
                val end = minOf(b.size.toLong(), start.toLong() + size).toInt()
                if (b.matches(pos, "EXIF")) {
                    // The note is stored as a TIFF tag; the JSON object itself is easy to find.
                    val open = (start until end).firstOrNull { b[it] == '{'.code.toByte() } ?: return null
                    val close = (end - 1 downTo open).firstOrNull { b[it] == '}'.code.toByte() } ?: return null
                    if (close - open > MAX_JSON) return null
                    return String(b, open, close - open + 1, Charsets.UTF_8)
                }
                pos = start + size + (size and 1)
            }
            return null
        }

        private fun ByteArray.matches(at: Int, tag: String): Boolean =
            at + tag.length <= size && tag.indices.all { this[at + it] == tag[it].code.toByte() }

        private fun ByteArray.le32(at: Int): Int =
            (this[at].toInt() and 0xFF) or ((this[at + 1].toInt() and 0xFF) shl 8) or
                ((this[at + 2].toInt() and 0xFF) shl 16) or ((this[at + 3].toInt() and 0xFF) shl 24)

        private const val STRING = """"((?:[^"\\]|\\.)*)""""

        private fun field(json: String, key: String): String? =
            Regex(""""${Regex.escape(key)}"\s*:\s*$STRING""").find(json)?.groupValues?.get(1)
                ?.let(::unescape)?.trim()?.take(MAX_FIELD)?.takeIf { it.isNotEmpty() }

        private fun emojis(json: String): List<String> {
            val array = Regex(""""emojis"\s*:\s*\[([^\]]*)]""").find(json)?.groupValues?.get(1) ?: return emptyList()
            return Regex(STRING).findAll(array).map { unescape(it.groupValues[1]) }.toList()
        }

        /** Decodes JSON string escapes, including \uXXXX (sticker apps often escape Hebrew). */
        internal fun unescape(s: String): String {
            val out = StringBuilder(s.length)
            var i = 0
            while (i < s.length) {
                val c = s[i]
                if (c != '\\' || i + 1 >= s.length) {
                    out.append(c)
                    i++
                    continue
                }
                when (val e = s[i + 1]) {
                    'u' -> {
                        val code = s.substring(i + 2, minOf(i + 6, s.length)).toIntOrNull(16)
                        if (code != null && i + 6 <= s.length) {
                            out.append(code.toChar())
                            i += 6
                            continue
                        }
                        out.append(e)
                    }
                    'n', 't', 'r' -> out.append(' ')
                    else -> out.append(e)
                }
                i += 2
            }
            return out.toString()
        }
    }
}

/** Search words for the emojis sticker packs file stickers under. */
object EmojiWords {

    private val WORDS: Map<String, String> = mapOf(
        "😂" to "laughing funny צוחק מצחיק",
        "🤣" to "laughing funny צוחק מצחיק",
        "😆" to "laughing צוחק",
        "😀" to "happy smile שמח חיוך",
        "😃" to "happy smile שמח חיוך",
        "😄" to "happy smile שמח חיוך",
        "😁" to "grin smile חיוך",
        "😊" to "happy smile שמח חיוך",
        "🙂" to "smile חיוך",
        "😉" to "wink קריצה",
        "😍" to "love אהבה מאוהב",
        "🥰" to "love אהבה מאוהב",
        "😘" to "kiss נשיקה",
        "😋" to "yummy טעים",
        "😜" to "silly crazy משוגע",
        "🤪" to "crazy משוגע",
        "🤔" to "thinking חושב",
        "🤨" to "suspicious חשדן",
        "😐" to "meh neutral אדיש",
        "😑" to "annoyed meh אדיש",
        "🙄" to "eye roll annoyed נמאס",
        "😏" to "smirk",
        "😴" to "sleep tired לישון עייף",
        "🥱" to "tired bored עייף משועמם",
        "😪" to "tired sleepy עייף",
        "🤢" to "disgusted gross מגעיל",
        "🤮" to "disgusted gross מגעיל",
        "🥵" to "hot חם",
        "🥶" to "cold קר",
        "😎" to "cool סבבה",
        "🤓" to "nerd חנון",
        "😕" to "confused מבולבל",
        "😟" to "worried מודאג",
        "🙁" to "sad עצוב",
        "☹" to "sad עצוב",
        "😮" to "surprised wow מופתע",
        "😲" to "shocked מופתע בהלם",
        "😳" to "embarrassed shocked נבוך",
        "🥺" to "please cute בבקשה",
        "😨" to "scared מפחד",
        "😰" to "scared worried מפחד",
        "😢" to "sad crying עצוב בוכה",
        "😭" to "crying sad בוכה עצוב",
        "😱" to "scream shocked צועק בהלם",
        "😩" to "tired frustrated עייף מתוסכל",
        "😤" to "angry frustrated כועס",
        "😡" to "angry כועס",
        "😠" to "angry כועס",
        "🤬" to "swearing angry מקלל כועס",
        "💀" to "dead dying מת",
        "🤡" to "clown ליצן",
        "💩" to "poop קקי",
        "👻" to "ghost רוח",
        "🙈" to "embarrassed shy נבוך",
        "🤗" to "hug חיבוק",
        "🤭" to "oops giggle אופס",
        "🤫" to "shh quiet שקט",
        "🥳" to "party celebrate מסיבה חגיגה",
        "🎉" to "party celebrate congrats מסיבה חגיגה מזלט",
        "🎂" to "birthday cake יומולדת עוגה",
        "🎁" to "gift present מתנה",
        "❤" to "love heart אהבה לב",
        "💕" to "love heart אהבה לב",
        "💔" to "heartbroken sad שבור לב",
        "🔥" to "fire hot lit אש",
        "💯" to "hundred perfect מושלם",
        "✨" to "sparkle",
        "👍" to "like ok yes לייק אוקי",
        "👎" to "dislike no לא",
        "👌" to "ok perfect אוקי מושלם",
        "👏" to "applause clap bravo כפיים כל הכבוד",
        "🙏" to "please thanks pray תודה בבקשה",
        "💪" to "strong power חזק כוח",
        "👋" to "hi bye wave היי ביי שלום",
        "✌" to "peace שלום",
        "🤞" to "fingers crossed luck בהצלחה",
        "👀" to "eyes looking מסתכל",
        "🤷" to "shrug whatever idk לא יודע",
        "🤦" to "facepalm ugh",
        "🙌" to "hooray yay יש",
        "☕" to "coffee morning קפה בוקר",
        "🍕" to "pizza פיצה",
        "🍺" to "beer בירה",
        "🍷" to "wine יין",
        "💤" to "sleep לישון",
        "🌙" to "night לילה",
        "☀" to "morning sun בוקר שמש",
        "🐱" to "cat חתול",
        "🐶" to "dog כלב",
        "💋" to "kiss נשיקה",
        "😈" to "evil devil שטן",
        "😇" to "angel innocent מלאך",
        "🤯" to "mind blown shocked בהלם",
        "😬" to "awkward oops מביך",
        "🤐" to "silent quiet שקט",
        "🤑" to "money rich כסף",
        "💰" to "money כסף",
        "✅" to "yes done כן",
        "❌" to "no לא",
        "⚽" to "football soccer כדורגל",
    )

    /** Words for [emoji], ignoring skin tones and the variation selector. */
    fun of(emoji: String): List<String> {
        val base = emoji.filterNot { it == '️' || it == '‍' }
            .let { s -> s.codePoints().toArray().filterNot { it in 0x1F3FB..0x1F3FF } }
            .firstOrNull()?.let { String(Character.toChars(it)) } ?: return emptyList()
        return WORDS[base]?.split(' ').orEmpty()
    }
}
