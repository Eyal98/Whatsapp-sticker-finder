package com.eyal98.stickerfinder.data

/**
 * Learned tags turned around for review: per tag, the stickers it was suggested on. The user
 * approves a whole group at once instead of opening each sticker, and every approval gives the
 * tag more examples to learn from.
 */
object TagSuggestions {

    /** One of the user's tags, suggested on [stickers] that look like the ones that have it. */
    data class Group(val tag: String, val stickers: List<StickerEntity>)

    /**
     * Groups [stickers]' learned tags by tag (ignoring case), leaving out tags a sticker already
     * has or that the user hid on it, and showing copies of one picture once. Biggest groups first.
     */
    fun group(stickers: List<StickerEntity>): List<Group> {
        val byTag = LinkedHashMap<String, MutableList<StickerEntity>>()
        val spelling = HashMap<String, String>()
        for (s in stickers) {
            val own = UserTags.parse(s.userTags).map { it.lowercase() }.toSet()
            val hidden = ImageTagFilter.split(s.removedImageTags).map { it.lowercase() }.toSet()
            for (tag in ImageTagFilter.split(s.learnedTags)) {
                val key = tag.lowercase()
                if (key in own || key in hidden) continue
                spelling.putIfAbsent(key, tag)
                val list = byTag.getOrPut(key) { mutableListOf() }
                // Copies of one picture (WhatsApp keeps several) show once.
                if (list.none { it.id == s.id || StickerRepository.imageKey(it) == StickerRepository.imageKey(s) }) list += s
            }
        }
        return byTag.map { (key, list) -> Group(spelling.getValue(key), list) }
            .sortedWith(compareByDescending<Group> { it.stickers.size }.thenBy { it.tag.lowercase() })
    }
}
