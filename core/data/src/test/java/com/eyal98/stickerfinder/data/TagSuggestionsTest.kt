package com.eyal98.stickerfinder.data

import org.junit.Assert.assertEquals
import org.junit.Test

class TagSuggestionsTest {

    private fun sticker(id: Long, learned: String?, own: String = "", hidden: String? = null) = StickerEntity(
        id = id, documentUri = "u$id", displayName = "d", sizeBytes = 1, lastModified = 1,
        userTags = own, learnedTags = learned, removedImageTags = hidden,
    )

    @Test
    fun `stickers are grouped by suggested tag, biggest group first`() {
        val groups = TagSuggestions.group(
            listOf(
                sticker(1, "Kermit"),
                sticker(2, "kermit, Elmo"),
                sticker(3, "Elmo"),
                sticker(4, "Kermit"),
            ),
        )
        assertEquals(listOf("Kermit", "Elmo"), groups.map { it.tag })
        assertEquals(listOf(1L, 2L, 4L), groups[0].stickers.map { it.id })
        assertEquals(listOf(2L, 3L), groups[1].stickers.map { it.id })
    }

    @Test
    fun `tags a sticker already has or had hidden aren't suggested`() {
        val groups = TagSuggestions.group(
            listOf(
                sticker(1, "Kermit", own = "kermit"),
                sticker(2, "Kermit", hidden = "KERMIT"),
                sticker(3, "Kermit"),
            ),
        )
        assertEquals(listOf(3L), groups.single().stickers.map { it.id })
    }

    @Test
    fun `count matches the groups, from the tag fields alone`() {
        val stickers = listOf(
            sticker(1, "Kermit", own = "kermit"),
            sticker(2, "kermit, Elmo"),
            sticker(3, "Elmo", hidden = "elmo"),
            sticker(4, "Kermit, Oscar"),
        )
        val states = stickers.map { StickerTagState(it.id, it.userTags, it.learnedTags, it.removedImageTags) }
        assertEquals(TagSuggestions.group(stickers).size, TagSuggestions.count(states))
        assertEquals(3, TagSuggestions.count(states))
    }
}
