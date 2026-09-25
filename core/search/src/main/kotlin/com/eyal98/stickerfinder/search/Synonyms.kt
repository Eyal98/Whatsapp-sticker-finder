package com.eyal98.stickerfinder.search

/**
 * Bilingual slang and synonym expansion for queries. Keys and values are normalized tokens
 * (final letters mapped, lowercase). Phase 2 moves this to an editable file.
 */
object Synonyms {

    private val GROUPS: List<Set<String>> = listOf(
        setOf("חחח", "lol", "haha", "צחוק", "laugh", "funny", "מצחיק"),
        setOf("סבבה", "ok", "okay", "cool", "אוקי", "בסדר"),
        setOf("יאללה", "yalla", "go", "קדימה"),
        setOf("מזלט", "congrats", "congratulations", "mazal"),
        setOf("תודה", "thanks", "thank", "thx"),
        setOf("עצוב", "sad", "בוכה", "crying", "cry"),
        setOf("כועס", "angry", "mad", "עצבני"),
        setOf("אהבה", "love", "לב", "heart"),
        setOf("בוקר", "morning"),
        setOf("לילה", "night", "sleep", "לישון"),
        setOf("חתול", "cat", "kitten"),
        setOf("כלב", "dog", "puppy"),
        // English contractions lose the apostrophe in normalization ("can't" -> "cant").
        // Single words only: each entry becomes one FTS term.
        setOf("cannot", "cant"),
        setOf("sorry", "סליחה", "מצטער", "מצטערת"),
    ).map { group -> group.map { TextNormalizer.normalize(it) }.toSet() }

    private val INDEX: Map<String, Set<String>> = buildMap {
        for (group in GROUPS) for (term in group) put(term, (getOrDefault(term, emptySet()) + group))
    }

    /** Returns synonyms of [token], not including the token itself. */
    fun of(token: String): Set<String> = INDEX[token].orEmpty() - token
}
