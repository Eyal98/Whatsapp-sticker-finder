package com.eyal98.stickerfinder.search

import kotlin.math.pow

/**
 * How well a sticker matches the query's words, from 0 to 1: every query word found in the
 * sticker's most telling field scores 1. Used to order keyword results, which the full-text index
 * returns unranked; without it a sticker matching one filler word of a long message could come
 * before one matching all of them.
 */
object KeywordRelevance {

    /** Field weights: the user's own words say the most, then what the models found. */
    const val USER = 3.0 // own tags, people's names, own description
    const val SEEN = 2.0 // picture tags (learned ones too), pack name, printed text
    const val HINT = 1.0 // emoji words, old descriptions

    /** A sticker's text, one entry per field with its weight. */
    class Field(val text: String?, val weight: Double)

    fun score(terms: List<QueryTerm>, fields: List<Field>): Double {
        if (terms.isEmpty()) return 0.0
        val best = fields.maxOfOrNull { it.weight } ?: return 0.0
        return termWeights(terms, fields).sum() / (terms.size * best)
    }

    /** Whether every query word is found in some field: the sticker is already found by it. */
    fun matchesAll(terms: List<QueryTerm>, fields: List<Field>): Boolean =
        terms.isNotEmpty() && termWeights(terms, fields).all { it > 0.0 }

    /** For each query word, the weight of the best field it's found in (0 if none). */
    private fun termWeights(terms: List<QueryTerm>, fields: List<Field>): List<Double> {
        val tokenized = fields.mapNotNull { f ->
            f.text?.takeIf { it.isNotBlank() }?.let { text ->
                TextNormalizer.tokenize(text).flatMap { HebrewPrefixes.variants(it) }.toHashSet() to f.weight
            }
        }
        return terms.mapIndexed { i, term ->
            // The last word may still be being typed: it also matches as a prefix.
            val isLast = i == terms.lastIndex
            tokenized.filter { (tokens, _) ->
                term.alternatives.any { it in tokens } ||
                    (isLast && tokens.any { it.startsWith(term.original) })
            }.maxOfOrNull { it.second } ?: 0.0
        }
    }
}

/** A sticker the user sent after searching for [queryKey], [count] times, last at [lastAt]. */
data class Pick(val queryKey: String, val stickerId: Long, val count: Int, val lastAt: Long)

/**
 * Learns from what the user picks: a sticker they sent after a search is ranked higher the next
 * time they search the same thing, a prefix of it ("kerm" for "kermit"), or mostly the same words.
 * Picks fade with a half-life of [HALF_LIFE_DAYS], so habits can change.
 */
object PickRanking {

    const val HALF_LIFE_DAYS = 60.0
    private const val DAY_MILLIS = 24 * 60 * 60 * 1000.0

    /** How similar two searches must be for one's picks to count for the other. */
    private const val EXACT = 1.0
    private const val PREFIX = 0.6
    private const val SHARED_WORDS = 0.4

    /** The normalized form a search is remembered by: its words, lower case, without niqqud. */
    fun key(query: String): String = TextNormalizer.tokenize(query).joinToString(" ")

    /** Sticker ids ranked by how often (and how recently) they were picked for searches like [query]. */
    fun rank(query: String, picks: List<Pick>, now: Long): List<Long> {
        val key = key(query)
        if (key.isEmpty()) return emptyList()
        val words = key.split(' ').toSet()
        val scores = HashMap<Long, Double>()
        for (pick in picks) {
            val match = similarity(key, words, pick.queryKey)
            if (match == 0.0) continue
            val ageDays = ((now - pick.lastAt).coerceAtLeast(0) / DAY_MILLIS)
            val weight = match * pick.count * 0.5.pow(ageDays / HALF_LIFE_DAYS)
            scores.merge(pick.stickerId, weight, Double::plus)
        }
        return scores.entries.sortedByDescending { it.value }.map { it.key }
    }

    private fun similarity(key: String, words: Set<String>, other: String): Double {
        if (other == key) return EXACT
        // Typing on: "kerm" should find what was picked for "kermit", and the other way round.
        if (key.length >= 2 && other.length >= 2 && (other.startsWith(key) || key.startsWith(other))) return PREFIX
        val otherWords = other.split(' ').toSet()
        val shared = words.intersect(otherWords).size
        if (shared == 0) return 0.0
        val jaccard = shared.toDouble() / (words.size + otherWords.size - shared)
        return if (jaccard >= 0.5) SHARED_WORDS * jaccard else 0.0
    }
}

/**
 * Turns a search the user keeps sending the same sticker for into a tag on that sticker, so it is
 * found by those words everywhere (meaning search and look-alikes too), not just ranked higher for
 * that exact search. Only short searches that don't already find the sticker by its own text: a
 * search like "cat" for a sticker tagged "cat" adds nothing, and neither does "kerm" for one
 * tagged "Kermit".
 */
object SearchTags {

    /** Sends of one sticker for one search before the search becomes its tag. */
    const val PICKS = 2
    const val MAX_WORDS = 3
    const val MAX_LENGTH = 30
    private const val MIN_WORD_LENGTH = 2

    /**
     * The tag to add for search [query], as the user typed it, on a sticker with text [fields],
     * or null when it shouldn't become a tag.
     */
    fun tagFor(query: String, fields: List<KeywordRelevance.Field>): String? {
        val words = TextNormalizer.tokenize(query)
        if (words.isEmpty() || words.size > MAX_WORDS) return null
        if (words.any { it.length < MIN_WORD_LENGTH || it.all(Char::isDigit) }) return null
        // As typed (final letters and capitals kept), only the spacing tidied.
        val tag = query.trim().split(WHITESPACE).joinToString(" ")
        if (tag.length > MAX_LENGTH) return null
        if (KeywordRelevance.matchesAll(QueryParser.parse(query), fields)) return null
        return tag
    }

    private val WHITESPACE = Regex("\\s+")
}
