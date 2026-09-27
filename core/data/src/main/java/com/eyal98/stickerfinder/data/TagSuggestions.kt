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
     * has or that the user hid on it. Biggest groups first.
     */
    fun group(stickers: List<StickerEntity>): List<Group> {
        val byTag = LinkedHashMap<String, MutableList<StickerEntity>>()
        val spelling = HashMap<String, String>()
        for (s in stickers) {
            for (tag in suggested(s.userTags, s.learnedTags, s.removedImageTags)) {
                val key = tag.lowercase()
                spelling.putIfAbsent(key, tag)
                val list = byTag.getOrPut(key) { mutableListOf() }
                if (list.none { it.id == s.id }) list += s
            }
        }
        return byTag.map { (key, list) -> Group(spelling.getValue(key), list) }
            .sortedWith(compareByDescending<Group> { it.stickers.size }.thenBy { it.tag.lowercase() })
    }

    /** How many groups [group] makes, from the tag fields alone (no need to load whole stickers). */
    fun count(stickers: List<StickerTagState>): Int =
        stickers.flatMapTo(HashSet()) { s -> suggested(s.userTags, s.learnedTags, s.removedImageTags).map { it.lowercase() } }.size

    /** A sticker's learned tags, without the ones it already has or the user hid on it. */
    private fun suggested(userTags: String, learnedTags: String?, removedImageTags: String?): List<String> {
        val own = UserTags.parse(userTags).map { it.lowercase() }.toSet()
        val hidden = ImageTagFilter.split(removedImageTags).map { it.lowercase() }.toSet()
        return ImageTagFilter.split(learnedTags).filter { it.lowercase() !in own && it.lowercase() !in hidden }
    }
}
