package com.eyal98.stickerfinder.data

import org.junit.Assert.assertEquals
import org.junit.Test

class UserTagsTest {

    @Test
    fun `reads old space-separated tags one word each`() {
        assertEquals(listOf("cat", "sad", "חתול"), UserTags.parse("cat sad חתול"))
    }

    @Test
    fun `multi-word tags survive a round trip`() {
        val tags = listOf("Kermit the Frog", "הפשוטע והפיג'מות", "sad")
        assertEquals(tags, UserTags.parse(UserTags.format(tags)))
    }

    @Test
    fun `repeats are dropped ignoring case`() {
        assertEquals("Yes, no", UserTags.format(listOf("Yes", "yes", " no ")))
    }

    @Test
    fun `hidden picture tags are left out, ignoring case`() {
        assertEquals("dog, כלב", ImageTagFilter.visible("cat, dog, חתול, כלב", "Cat, חתול"))
    }

    @Test
    fun `learned tags join picture tags once, and can be hidden`() {
        assertEquals("cat, חתול, Kermit", ImageTagFilter.join("cat, חתול", "Kermit, kermit"))
        assertEquals("cat", ImageTagFilter.join("cat", null))
        assertEquals(null, ImageTagFilter.join(null, ""))
        val sticker = StickerEntity(documentUri = "u", displayName = "d", sizeBytes = 1, lastModified = 1,
            imageTags = "cat", learnedTags = "Kermit", removedImageTags = "kermit")
        assertEquals("cat", sticker.visibleImageTags)
    }
}
