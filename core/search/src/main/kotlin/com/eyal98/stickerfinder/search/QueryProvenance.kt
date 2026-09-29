package com.eyal98.stickerfinder.search

/**
 * Where the keyboard's search came from. When the keyboard opens, its search is the text already
 * in the chat box, which must never be saved: not as search history, and not as a tag learned
 * from it. Editing that search (a letter more, a letter less) still leaves chat text in it, so it
 * stays unlearnable until the user clears it completely and types a search of their own.
 */
class QueryProvenance {

    /** The search is exactly the chat box's text, which is removed from the chat after sending. */
    var isFieldText: Boolean = false
        private set

    /** The search holds some chat text: nothing is learned from it. */
    var hasFieldText: Boolean = false
        private set

    /** Search learns from this search (search history, tags from searches). */
    val learnable: Boolean get() = !hasFieldText

    /** The keyboard opened with [fieldText] as its search. */
    fun opened(fieldText: String) {
        isFieldText = fieldText.isNotEmpty()
        hasFieldText = isFieldText
    }

    /** The user changed the search to [query] on the keyboard's own keys. */
    fun edited(query: String) {
        isFieldText = false
        if (query.isEmpty()) hasFieldText = false
    }
}
