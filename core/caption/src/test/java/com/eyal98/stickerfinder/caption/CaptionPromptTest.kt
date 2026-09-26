package com.eyal98.stickerfinder.caption

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CaptionPromptTest {

    @Test
    fun `parses the requested format`() {
        val caption = CaptionPrompt.parse(
            """
            EN: A crying cartoon cat saying sorry.
            HE: חתול מצויר בוכה שאומר סליחה.
            TAGS: cat, sad, sorry, חתול, עצוב, סליחה
            """.trimIndent(),
        )
        assertEquals("A crying cartoon cat saying sorry", caption?.english)
        assertEquals("חתול מצויר בוכה שאומר סליחה", caption?.hebrew)
        assertEquals(listOf("cat", "sad", "sorry", "חתול", "עצוב", "סליחה"), caption?.tags)
    }

    @Test
    fun `ignores the model's reasoning before the answer`() {
        val caption = CaptionPrompt.parse(
            "<|channel>thought\nEN: draft\n<channel|>EN: A happy frog\nTAGS: frog, happy",
        )
        assertEquals("A happy frog", caption?.english)
        assertEquals(listOf("frog", "happy"), caption?.tags)
    }

    @Test
    fun `tolerates markdown, other label names and chatter`() {
        val caption = CaptionPrompt.parse(
            """
            Sure! Here is the description:
            **English**: "A dog dancing"
            - Hebrew - כלב רוקד
            Keywords: #dog; #dance | party
            """.trimIndent(),
        )
        assertEquals("A dog dancing", caption?.english)
        assertEquals("כלב רוקד", caption?.hebrew)
        assertEquals(listOf("dog", "dance", "party"), caption?.tags)
    }

    @Test
    fun `keeps the first value when a label repeats and drops overlong tags`() {
        val longTag = "x".repeat(41)
        val caption = CaptionPrompt.parse("EN: first\nEN: second\nTAGS: ok, $longTag, ok")
        assertEquals("first", caption?.english)
        assertEquals(listOf("ok"), caption?.tags)
    }

    @Test
    fun `recognized names come first in the tags and none is dropped`() {
        val caption = CaptionPrompt.parse("EN: SpongeBob laughing\nNAMES: SpongeBob, Patrick Star\nTAGS: laughing, בובספוג, none")
        assertEquals(listOf("SpongeBob", "Patrick Star", "laughing", "בובספוג"), caption?.tags)
        assertEquals(emptyList<String>(), CaptionPrompt.parse("EN: a cat\nNAMES: none")?.tags)
    }

    @Test
    fun `generic keywords, the pack name and case-only repeats are dropped`() {
        val caption = CaptionPrompt.parse(
            "EN: JoJo nodding\nTAGS: JoJo, jojo, yes, Yes, meme, yes sticker pack, funny, JoJo pack, agreement",
            exclude = listOf("JoJo pack"),
        )
        assertEquals(listOf("JoJo", "yes", "agreement"), caption?.tags)
    }

    @Test
    fun `prompt says not to transcribe the printed text`() {
        assertTrue(CaptionPrompt.build("שלום").contains("Do not quote, transcribe or translate them"))
    }

    @Test
    fun `prompt includes the pack name when known`() {
        assertFalse(CaptionPrompt.build(null, null).contains("sticker pack named"))
        assertTrue(CaptionPrompt.build(null, "Friends \"TV\"").contains("\"Friends 'TV'\""))
    }

    @Test
    fun `reply without any known label is rejected`() {
        assertNull(CaptionPrompt.parse("I can't see the image."))
        assertNull(CaptionPrompt.parse(""))
    }

    @Test
    fun `prompt includes OCR hint only when present, with quotes neutralized`() {
        assertFalse(CaptionPrompt.build(null).contains("OCR"))
        val prompt = CaptionPrompt.build("מזל\"ט")
        assertTrue(prompt.contains("\"מזל'ט\""))
    }
}
