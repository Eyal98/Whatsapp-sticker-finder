package com.eyal98.stickerfinder.search

/**
 * Updates a list someone is looking at without reshuffling it: items still in [next] keep their
 * place in [shown] (updated to their [next] version, e.g. a new star), items no longer in [next]
 * leave, and new ones are added at the end in [next]'s order. For results that change because
 * background work finished more stickers, not because the user searched for something else.
 */
object StableOrder {
    fun <T, K> merge(shown: List<T>, next: List<T>, key: (T) -> K): List<T> {
        val byKey = next.associateBy(key)
        val kept = shown.mapNotNull { byKey[key(it)] }
        val keptKeys = kept.mapTo(HashSet(), key)
        return kept + next.filter { key(it) !in keptKeys }
    }
}
