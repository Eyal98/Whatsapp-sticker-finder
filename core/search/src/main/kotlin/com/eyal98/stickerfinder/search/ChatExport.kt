package com.eyal98.stickerfinder.search

import java.time.DateTimeException
import java.time.LocalDate

/**
 * One message from a WhatsApp chat export. Who sent it isn't kept: learning uses messages from
 * anyone, and names and phone numbers have no business in memory longer than one line.
 */
class ChatMessage(
    /** Seconds on the chat's local clock (no time zone), or null when the date didn't parse. */
    val time: Long?,
    val kind: Kind,
    /** The message, for [Kind.TEXT]; empty otherwise. */
    val text: String = "",
    /** The sticker's file name in the export, for [Kind.STICKER]. */
    val file: String? = null,
) {
    enum class Kind {
        /** Something someone wrote. */
        TEXT,

        /** A sticker whose file is in the export. */
        STICKER,

        /** A photo, voice note, document, "<Media omitted>", deleted message and the like. */
        OTHER,

        /** "X added Y", "Messages are end-to-end encrypted", …: no sender. */
        SYSTEM,
    }
}

/**
 * Reads the chat's .txt from a WhatsApp export. The format depends on the phone and its language:
 * Android writes `12/31/24, 21:05 - Name: text` (or `31/12/2024, 21:05 - …`, `31.12.24, …`), iOS
 * `[31/12/2024, 21:05:33] Name: text`, either may use 12-hour times, and right-to-left languages
 * sprinkle direction marks around the date and the name. A line that doesn't start with a date
 * continues the message before it.
 *
 * Attachments are named in the text with a note in the phone's language
 * (`STK-20241231-WA0003.webp (file attached)`, `(קובץ מצורף)`, iOS `<attached: …webp>`), so
 * rather than recognizing every wording, a message is a sticker when it names a `.webp` file that
 * is in the export. Odd lines never fail the parse: at worst they're read as text or skipped.
 */
object ChatParser {

    enum class DateOrder { DAY_MONTH_YEAR, MONTH_DAY_YEAR, YEAR_MONTH_DAY }

    /** Direction and formatting marks that right-to-left exports put around dates and names. */
    private const val MARKS = """[\u200E\u200F\u202A-\u202E\u2066-\u2069\uFEFF]"""
    private const val M = "$MARKS*"
    private const val SPACE = """[\s\u00A0\u202F]"""
    private const val AM_PM = """[AaPp]\.?$SPACE?[Mm]\.?|לפנה["״]צ|אחה["״]צ"""

    private val HEADER = Regex(
        """^$M(\[)?$M(\d{1,4})[./-](\d{1,2})[./-](\d{1,4})$M,?$SPACE*$M""" +
            """(\d{1,2})[:.](\d{2})(?:[:.](\d{2}))?$SPACE*($AM_PM)?$M(?:(\])|$SPACE*[-–])$M$SPACE*""",
    )
    private val ALL_MARKS = Regex(MARKS)

    /** File names as WhatsApp writes them; direction marks and spaces end a name. */
    private val FILE_NAME = Regex("""[\p{L}\p{N}_.\-]+\.[A-Za-z0-9]{2,5}""")

    /** `name.ext (a note)`: an attachment whose file isn't in the export. */
    private val ATTACHMENT_NOTE = Regex("""^\S+\.[A-Za-z0-9]{2,5}$SPACE*\([^()]{1,40}\)$SPACE*$""")

    /** `<Media omitted>`, `<מדיה הושמטה>`, `<attached: …>` and other notes in angle brackets. */
    private val ANGLE_NOTE = Regex("""^<[^<>]{1,60}>$""")

    /** `<This message was edited>` and its translations, after the text. */
    private val TRAILING_NOTE = Regex("""$SPACE*<[^<>]{1,40}>$""")

    /** Notes that stand in for a message: iOS "image omitted", deleted messages, view-once media. */
    private val PLACEHOLDER = Regex(
        """^(?:.{0,20}\bomitted|.{0,20}הושמט[הו]?|this message was deleted|you deleted this message|""" +
            """הודעה זו נמחקה|מחקת את ההודעה הזו|null)$""",
        RegexOption.IGNORE_CASE,
    )

    /** A sender name longer than this means the colon was part of a system line. */
    private const val MAX_NAME = 60

    private class Header(
        val bracketed: Boolean,
        val a: String,
        val b: String,
        val c: String,
        val hour: Int,
        val minute: Int,
        val second: Int,
        val amPm: String?,
        val rest: String,
    )

    private fun header(line: String): Header? {
        val m = HEADER.find(line) ?: return null
        val g = m.groupValues
        return Header(
            bracketed = g[1].isNotEmpty() && g[9].isNotEmpty(),
            a = g[2], b = g[3], c = g[4],
            hour = g[5].toInt(), minute = g[6].toInt(), second = g[7].toIntOrNull() ?: 0,
            amPm = g[8].ifEmpty { null },
            rest = line.substring(m.range.last + 1),
        )
    }

    /**
     * Whether dates are day-first or month-first, from the dates themselves: a first number over
     * 12 must be a day, a second one over 12 must be one. Day-first when nothing tells.
     */
    fun dateOrder(lines: Sequence<String>): DateOrder {
        for (line in lines) {
            val h = header(line) ?: continue
            if (h.a.length == 4) return DateOrder.YEAR_MONTH_DAY
            if (h.a.toInt() > 12) return DateOrder.DAY_MONTH_YEAR
            if (h.b.toInt() > 12) return DateOrder.MONTH_DAY_YEAR
        }
        return DateOrder.DAY_MONTH_YEAR
    }

    /**
     * The chat's messages in order. [files] are the names of the files in the export (any
     * folder); a message naming one of its `.webp` files is a sticker send.
     */
    fun parse(text: String, files: Collection<String>): Sequence<ChatMessage> {
        val order = dateOrder(text.lineSequence())
        val byLowerName = HashMap<String, String>()
        for (f in files) byLowerName[f.substringAfterLast('/').lowercase()] = f.substringAfterLast('/')
        return sequence {
            var current: Header? = null
            val body = StringBuilder()
            for (line in text.lineSequence()) {
                val h = header(line)
                if (h == null) {
                    // A line of a longer message (or junk before the first message).
                    if (current != null) body.append('\n').append(line)
                    continue
                }
                current?.let { yield(message(it, body.toString(), order, byLowerName)) }
                current = h
                body.setLength(0)
                body.append(h.rest)
            }
            current?.let { yield(message(it, body.toString(), order, byLowerName)) }
        }
    }

    private fun message(h: Header, rest: String, order: DateOrder, files: Map<String, String>): ChatMessage {
        val time = time(h, order)
        // "Name: text" on the first line; a line without a sender is a system message.
        val firstLine = rest.substringBefore('\n')
        val colon = firstLine.indexOf(": ").let { if (it < 0 && firstLine.trimEnd().endsWith(":")) firstLine.trimEnd().length - 1 else it }
        if (colon < 0 || colon > MAX_NAME) return ChatMessage(time, ChatMessage.Kind.SYSTEM)
        val raw = rest.substring(minOf(colon + 2, rest.length))
        val names = FILE_NAME.findAll(raw).map { it.value.lowercase() }.toList()
        names.firstOrNull { it.endsWith(".webp") && it in files }?.let {
            return ChatMessage(time, ChatMessage.Kind.STICKER, file = files.getValue(it))
        }
        if (names.any { it in files }) return ChatMessage(time, ChatMessage.Kind.OTHER)
        // iOS starts media notes and system lines (under the chat's name) with a direction mark.
        if (h.bracketed && raw.startsWith('\u200E')) return ChatMessage(time, ChatMessage.Kind.OTHER)
        val clean = ALL_MARKS.replace(raw, "").trim()
        val firstClean = clean.substringBefore('\n').trim()
        if (clean.isEmpty() || ANGLE_NOTE.matches(firstClean) || ATTACHMENT_NOTE.matches(firstClean) ||
            PLACEHOLDER.matches(clean)
        ) {
            return ChatMessage(time, ChatMessage.Kind.OTHER)
        }
        val text = TRAILING_NOTE.replace(clean, "").trim()
        if (text.isEmpty()) return ChatMessage(time, ChatMessage.Kind.OTHER)
        return ChatMessage(time, ChatMessage.Kind.TEXT, text = text)
    }

    private fun time(h: Header, order: DateOrder): Long? {
        val (y, mo, d) = when (order) {
            DateOrder.YEAR_MONTH_DAY -> Triple(h.a, h.b, h.c)
            DateOrder.MONTH_DAY_YEAR -> Triple(h.c, h.a, h.b)
            DateOrder.DAY_MONTH_YEAR -> Triple(h.c, h.b, h.a)
        }
        val year = y.toInt().let { if (it < 100) 2000 + it else it }
        var hour = h.hour
        h.amPm?.let { mark ->
            val pm = mark.startsWith("p", ignoreCase = true) || mark.startsWith("אחה")
            if (pm && hour < 12) hour += 12
            if (!pm && hour == 12) hour = 0
        }
        if (hour > 23 || h.minute > 59 || h.second > 59) return null
        return try {
            LocalDate.of(year, mo.toInt(), d.toInt()).toEpochDay() * 86_400L + hour * 3_600L + h.minute * 60L + h.second
        } catch (e: DateTimeException) {
            null
        }
    }
}

/**
 * What was said just before each sticker was sent: that's what the sticker was used for.
 */
object ChatContext {

    /** At most this many messages before a sticker. */
    const val MAX_MESSAGES = 3

    /** Only messages this recent: an older one was probably about something else. */
    const val WINDOW_SECONDS = 10 * 60L

    /** One sticker send's context is cut to this length. */
    const val MAX_CHARS = 300

    /** One sticker send; [context] is null when nothing was written just before it. */
    class StickerSend(val file: String, val context: String?)

    private val URL = Regex("""(?:https?://|www\.)\S+""", RegexOption.IGNORE_CASE)
    private val SPACES = Regex("""\s+""")

    /**
     * Every sticker send with up to [MAX_MESSAGES] text messages (from anyone) sent within
     * [WINDOW_SECONDS] before it, most recent first. Links are dropped: they say nothing about the
     * mood and would only add noise.
     */
    fun stickerSends(messages: Sequence<ChatMessage>): List<StickerSend> {
        val recent = ArrayDeque<Pair<Long?, String>>()
        val sends = ArrayList<StickerSend>()
        for (m in messages) {
            when (m.kind) {
                ChatMessage.Kind.TEXT -> {
                    val text = clean(m.text) ?: continue
                    recent.addLast(m.time to text)
                    if (recent.size > MAX_MESSAGES) recent.removeFirst()
                }
                ChatMessage.Kind.STICKER -> {
                    val at = m.time
                    val context = if (at == null) {
                        emptyList()
                    } else {
                        recent.reversed().filter { (t, _) -> t != null && at - t in 0..WINDOW_SECONDS }.map { it.second }
                    }
                    sends += StickerSend(m.file ?: continue, cut(context.joinToString("\n"), MAX_CHARS).ifEmpty { null })
                }
                ChatMessage.Kind.OTHER, ChatMessage.Kind.SYSTEM -> Unit
            }
        }
        return sends
    }

    /** The message without links and extra spaces, or null if nothing is left. */
    fun clean(text: String): String? =
        SPACES.replace(URL.replace(text, " "), " ").trim().ifEmpty { null }

    /** At most [max] characters, without splitting a surrogate pair (an emoji). */
    fun cut(text: String, max: Int): String {
        if (text.length <= max) return text
        val end = if (Character.isHighSurrogate(text[max - 1])) max - 1 else max
        return text.substring(0, end).trimEnd()
    }
}

/**
 * Turns a sticker's contexts from one chat into a "used for" meaning vector, and folds it into
 * what earlier chats taught, so every imported chat refines it.
 */
object ContextLearning {

    /** Text embedded at once; longer text dilutes the meaning and slows the model. */
    const val MAX_CHARS = 600

    /** A sticker sent hundreds of times needs only its latest uses to show what it's for. */
    const val MAX_CHUNKS = 6

    class Chunk(val text: String, val uses: Int)

    /**
     * Groups [contexts] (in chat order) into texts of at most [MAX_CHARS], newest first, at most
     * [MAX_CHUNKS] of them. [Chunk.uses] is how many sticker sends each one covers.
     */
    fun chunks(contexts: List<String>, maxChars: Int = MAX_CHARS, maxChunks: Int = MAX_CHUNKS): List<Chunk> {
        val chunks = ArrayList<Chunk>()
        val text = StringBuilder()
        var uses = 0
        for (context in contexts.asReversed()) {
            val piece = ChatContext.cut(context, maxChars)
            if (uses > 0 && text.length + 1 + piece.length > maxChars) {
                chunks += Chunk(text.toString(), uses)
                if (chunks.size == maxChunks) return chunks
                text.setLength(0)
                uses = 0
            }
            if (uses > 0) text.append('\n')
            text.append(piece)
            uses++
        }
        if (uses > 0) chunks += Chunk(text.toString(), uses)
        return chunks
    }

    /** The average of [vectors] weighted by their use counts; null when there are none. */
    fun mean(vectors: List<Pair<FloatArray, Int>>): FloatArray? {
        val total = vectors.sumOf { it.second }
        if (vectors.isEmpty() || total <= 0) return null
        val out = FloatArray(vectors.first().first.size)
        for ((v, uses) in vectors) {
            for (i in 0 until minOf(out.size, v.size)) out[i] += v[i] * uses / total
        }
        return out
    }

    /**
     * The running average after adding [addUses] uses averaging [add] to [oldUses] uses averaging
     * [old]. The result isn't normalized, so the next fold stays a true average; the search index
     * normalizes it when loading.
     */
    fun fold(old: FloatArray?, oldUses: Int, add: FloatArray, addUses: Int): FloatArray {
        if (old == null || oldUses <= 0 || old.size != add.size) return add.copyOf()
        val total = (oldUses + addUses).toFloat()
        return FloatArray(add.size) { i -> (old[i] * oldUses + add[i] * addUses) / total }
    }
}

/**
 * Finds a sticker in the library by its perceptual hash: the closest within [maxDistance]
 * differing bits (re-encoding, a forwarded sticker or another WhatsApp version can flip a bit or
 * two). Only when that's one picture: different pictures can share a perceptual hash (flat colour
 * variants, animations with the same first frame), so when the closest ones aren't copies of the
 * same file ([contents], each sticker's exact content hash, if known) it finds nothing rather than
 * guess.
 */
class PerceptualMatch(
    private val ids: LongArray,
    private val hashes: LongArray,
    private val maxDistance: Int = MAX_DISTANCE,
    private val contents: Array<String?> = arrayOfNulls(ids.size),
) {
    init {
        require(ids.size == hashes.size && contents.size == ids.size) { "One hash per id" }
    }

    /** The matching sticker's id, or null when none is close enough or it's ambiguous. */
    fun find(hash: Long): Long? {
        var bestDistance = maxDistance + 1
        val best = ArrayList<Int>()
        for (i in hashes.indices) {
            val d = java.lang.Long.bitCount(hash xor hashes[i])
            if (d < bestDistance) {
                bestDistance = d
                best.clear()
                best += i
            } else if (d == bestDistance) {
                best += i
            }
        }
        if (best.isEmpty()) return null
        if (best.size == 1) return ids[best[0]]
        // Several at the same distance: fine only if they're all the same file's copies.
        val sameFile = best.map { contents[it] }.distinct().let { it.size == 1 && !it[0].isNullOrEmpty() }
        return if (sameFile) ids[best[0]] else null
    }

    companion object {
        const val MAX_DISTANCE = 3
    }
}
