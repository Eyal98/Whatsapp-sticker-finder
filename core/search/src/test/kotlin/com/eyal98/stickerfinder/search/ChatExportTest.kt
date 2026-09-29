package com.eyal98.stickerfinder.search

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ChatExportTest {

    private val LRM = "\u200E"
    private val RLM = "\u200F"

    private fun parse(text: String, vararg files: String) = ChatParser.parse(text, files.toList()).toList()

    private fun kinds(messages: List<ChatMessage>) = messages.map { it.kind }

    @Test
    fun `android month-first export with a sticker`() {
        val chat = """
            12/31/24, 21:03 - Messages and calls are end-to-end encrypted. No one outside of this chat can read them.
            12/31/24, 21:04 - Dana: where are you??
            12/31/24, 21:05 - Eyal: STK-20241231-WA0003.webp (file attached)
        """.trimIndent()
        val m = parse(chat, "STK-20241231-WA0003.webp", "WhatsApp Chat with Dana.txt")
        assertEquals(listOf(ChatMessage.Kind.SYSTEM, ChatMessage.Kind.TEXT, ChatMessage.Kind.STICKER), kinds(m))
        assertEquals("where are you??", m[1].text)
        assertEquals("STK-20241231-WA0003.webp", m[2].file)
        assertEquals(60L, m[2].time!! - m[1].time!!)
    }

    @Test
    fun `day-first dates are told apart from month-first ones`() {
        val chat = """
            31/12/2024, 23:59 - Dana: late again
            01/01/2025, 00:01 - Eyal: STK-1.webp (file attached)
        """.trimIndent()
        assertEquals(ChatParser.DateOrder.DAY_MONTH_YEAR, ChatParser.dateOrder(chat.lineSequence()))
        val m = parse(chat, "STK-1.webp")
        assertEquals(120L, m[1].time!! - m[0].time!!)
    }

    @Test
    fun `month-first is recognized from the second number`() {
        val chat = "1/13/25, 10:00 - Dana: hi\n1/2/25, 10:00 - Dana: hi"
        assertEquals(ChatParser.DateOrder.MONTH_DAY_YEAR, ChatParser.dateOrder(chat.lineSequence()))
    }

    @Test
    fun `ios export with seconds and attached sticker`() {
        val chat = """
            [31/12/2024, 21:05:00] Dana: $LRM${"Messages and calls are end-to-end encrypted."}
            [31/12/2024, 21:05:10] Dana: running 20 min late sorry
            [31/12/2024, 21:05:33] Eyal: $LRM<attached: 00000012-STICKER-2024-12-31-21-05-33.webp>
            [31/12/2024, 21:06:00] Dana: ${LRM}image omitted
        """.trimIndent()
        val m = parse(chat, "00000012-STICKER-2024-12-31-21-05-33.webp", "_chat.txt")
        assertEquals(
            listOf(ChatMessage.Kind.OTHER, ChatMessage.Kind.TEXT, ChatMessage.Kind.STICKER, ChatMessage.Kind.OTHER),
            kinds(m),
        )
        assertEquals(23L, m[2].time!! - m[1].time!!)
    }

    @Test
    fun `twelve-hour times with am and pm`() {
        val chat = """
            12/31/24, 11:59 AM - Dana: almost noon
            12/31/24, 12:01 PM - Dana: noon
            12/31/24, 12:30 AM - Dana: night
            [12/31/24, 9:05:00${"\u202F"}PM] Dana: evening
        """.trimIndent()
        val m = parse(chat)
        assertEquals(4, m.size)
        assertEquals(120L, m[1].time!! - m[0].time!!)
        assertEquals(-(11 * 3600L + 29 * 60), m[2].time!! - m[0].time!!)
        assertEquals(21 * 3600L + 5 * 60, m[3].time!! - (m[0].time!! - (11 * 3600L + 59 * 60)))
    }

    @Test
    fun `hebrew export with direction marks`() {
        val chat = """
            ${RLM}31.12.2024, 21:04 - ${RLM}דני: אני מאחר בעשר דקות
            ${RLM}31.12.2024, 21:05 - ${RLM}איל: ${LRM}STK-20241231-WA0003.webp (קובץ מצורף)
            ${RLM}31.12.2024, 21:06 - ${RLM}דני: <מדיה הושמטה>
            ${RLM}31.12.2024, 21:07 - ${RLM}דני הוסיף/ה את רונית
        """.trimIndent()
        val m = parse(chat, "STK-20241231-WA0003.webp")
        assertEquals(
            listOf(ChatMessage.Kind.TEXT, ChatMessage.Kind.STICKER, ChatMessage.Kind.OTHER, ChatMessage.Kind.SYSTEM),
            kinds(m),
        )
        assertEquals("אני מאחר בעשר דקות", m[0].text)
        assertEquals("STK-20241231-WA0003.webp", m[1].file)
    }

    @Test
    fun `multi-line messages continue on the next lines`() {
        val chat = """
            31/12/2024, 21:04 - Dana: first line
            second line

            third line
            31/12/2024, 21:05 - Eyal: next
        """.trimIndent()
        val m = parse(chat)
        assertEquals(2, m.size)
        assertEquals("first line\nsecond line\n\nthird line", m[0].text)
    }

    @Test
    fun `other attachments, omitted media and deleted messages are not text`() {
        val chat = """
            31/12/2024, 21:00 - Dana: IMG-20241231-WA0001.jpg (file attached)
            31/12/2024, 21:01 - Dana: PTT-20241231-WA0002.opus (file attached)
            31/12/2024, 21:02 - Dana: <Media omitted>
            31/12/2024, 21:03 - Dana: This message was deleted
            31/12/2024, 21:04 - Dana: STK-20241231-WA0009.webp (file attached)
            31/12/2024, 21:05 - Dana: ok then <This message was edited>
        """.trimIndent()
        // The last sticker isn't in the export (exported without it): not a sticker send.
        val m = parse(chat, "IMG-20241231-WA0001.jpg")
        assertEquals(List(5) { ChatMessage.Kind.OTHER } + ChatMessage.Kind.TEXT, kinds(m))
        assertEquals("ok then", m[5].text)
    }

    @Test
    fun `odd lines don't fail the parse`() {
        val chat = "garbage before\n99/99/2024, 21:00 - Dana: bad date\n31/12/2024, 25:00 - Dana: bad hour"
        val m = parse(chat)
        assertEquals(2, m.size)
        assertNull(m[0].time)
        assertNull(m[1].time)
    }

    @Test
    fun `context is the last three recent messages, newest first, without links`() {
        val chat = """
            31/12/2024, 20:40 - Dana: too old
            31/12/2024, 20:58 - Dana: one https://example.com/x
            31/12/2024, 20:59 - Eyal: two
            31/12/2024, 21:00 - Dana: IMG-1.jpg (file attached)
            31/12/2024, 21:01 - Dana: three
            31/12/2024, 21:02 - Dana: four
            31/12/2024, 21:03 - Eyal: STK-1.webp (file attached)
            31/12/2024, 21:30 - Eyal: STK-1.webp (file attached)
        """.trimIndent()
        val sends = ChatContext.stickerSends(ChatParser.parse(chat, listOf("STK-1.webp", "IMG-1.jpg")))
        assertEquals(2, sends.size)
        assertEquals("four\nthree\ntwo", sends[0].context)
        assertNull(sends[1].context)
    }

    @Test
    fun `context window is ten minutes`() {
        val chat = """
            31/12/2024, 20:52 - Dana: eleven minutes before
            31/12/2024, 20:53 - Dana: ten minutes before
            31/12/2024, 20:58 - Dana: https://only.a/link
            31/12/2024, 21:03 - Eyal: STK-1.webp (file attached)
        """.trimIndent()
        val sends = ChatContext.stickerSends(ChatParser.parse(chat, listOf("STK-1.webp")))
        assertEquals("ten minutes before", sends.single().context)
    }

    @Test
    fun `context is cut to 300 characters`() {
        val long = "x".repeat(400)
        val chat = "31/12/2024, 21:00 - Dana: $long\n31/12/2024, 21:01 - Eyal: STK-1.webp (file attached)"
        val sends = ChatContext.stickerSends(ChatParser.parse(chat, listOf("STK-1.webp")))
        assertEquals(ChatContext.MAX_CHARS, sends.single().context!!.length)
    }

    @Test
    fun `cut doesn't split an emoji`() {
        val text = "ab😂"
        assertEquals("ab", ChatContext.cut(text, 3))
    }

    @Test
    fun `chunks take the newest contexts and stay short`() {
        val contexts = listOf("a".repeat(250), "b".repeat(250), "c".repeat(250), "d".repeat(250), "e")
        val chunks = ContextLearning.chunks(contexts, maxChars = 600, maxChunks = 2)
        assertEquals(2, chunks.size)
        assertEquals("e\n" + "d".repeat(250) + "\n" + "c".repeat(250), chunks[0].text)
        assertEquals(3, chunks[0].uses)
        assertEquals(2, chunks[1].uses)
        chunks.forEach { assert(it.text.length <= 600) }
    }

    @Test
    fun `running average weighs by uses`() {
        val first = ContextLearning.fold(null, 0, floatArrayOf(1f, 0f), 1)
        assertArrayEquals(floatArrayOf(1f, 0f), first, 1e-6f)
        val second = ContextLearning.fold(first, 1, floatArrayOf(0f, 1f), 3)
        assertArrayEquals(floatArrayOf(0.25f, 0.75f), second, 1e-6f)
        // Same as averaging all four uses at once.
        val all = ContextLearning.mean(listOf(floatArrayOf(1f, 0f) to 1, floatArrayOf(0f, 1f) to 3))!!
        assertArrayEquals(all, second, 1e-6f)
    }

    @Test
    fun `a vector from another model replaces the old one`() {
        val folded = ContextLearning.fold(floatArrayOf(1f, 0f, 0f), 5, floatArrayOf(0f, 1f), 1)
        assertArrayEquals(floatArrayOf(0f, 1f), folded, 0f)
    }

    @Test
    fun `perceptual match is exact first, then within three bits`() {
        val match = PerceptualMatch(longArrayOf(10, 20, 30), longArrayOf(0b1111L, 0b1111_0000_0000L, 0xFFFFL shl 40))
        assertEquals(10L, match.find(0b1111L))
        assertEquals(10L, match.find(0b1000L)) // 3 bits off
        assertEquals(20L, match.find(0b1111_0000_0001L)) // 1 bit off
        assertNull(match.find(0b1111_1111_1111_1111L shl 20))
        assertNull(match.find(0b1111L shl 50))
    }
}
