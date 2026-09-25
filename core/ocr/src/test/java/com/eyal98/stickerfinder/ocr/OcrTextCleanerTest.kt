package com.eyal98.stickerfinder.ocr

import com.eyal98.stickerfinder.ocr.OcrTextCleaner.Word
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class OcrTextCleanerTest {

    @Test
    fun `keeps confident words and strips surrounding punctuation`() {
        val reading = OcrTextCleaner.clean(listOf(Word("בוקר", 91f), Word("טוב!", 85f), Word("\"Hello,", 77f)))
        assertEquals("בוקר טוב Hello", reading?.text)
    }

    @Test
    fun `drops low-confidence words`() {
        val reading = OcrTextCleaner.clean(listOf(Word("מזל", 90f), Word("טוב", 30f)))
        assertEquals("מזל", reading?.text)
    }

    @Test
    fun `drops single characters, symbol runs and mixed-script noise`() {
        val words = listOf(Word("ו", 95f), Word("|/\\\\", 95f), Word("שa", 95f), Word("x#%!y", 95f))
        assertNull(OcrTextCleaner.clean(words))
    }

    @Test
    fun `keeps inner quote marks in abbreviations`() {
        assertEquals("מזל\"ט", OcrTextCleaner.clean(listOf(Word("מזל\"ט", 88f)))?.text)
    }

    @Test
    fun `best picks the reading with more confident text`() {
        val weak = OcrTextCleaner.clean(listOf(Word("lol", 62f)))
        val strong = OcrTextCleaner.clean(listOf(Word("חחח", 90f), Word("אני", 80f)))
        assertEquals(strong, OcrTextCleaner.best(weak, strong, null))
        assertNull(OcrTextCleaner.best(null, null))
    }
}
