package com.eyal98.stickerfinder.search

/** Builds the terms that are stored in the full-text index for a sticker. */
object IndexTerms {

    /**
     * Returns every normalized token of [texts] plus its Hebrew prefix-stripped variants, as a
     * space-separated string ready to store in the FTS table.
     */
    fun build(vararg texts: String?): String =
        texts.filterNotNull()
            .flatMap { TextNormalizer.tokenize(it) }
            .flatMap { HebrewPrefixes.variants(it) }
            .distinct()
            .joinToString(" ")
}

/** One word of the user's query plus the alternatives that also count as a match for it. */
data class QueryTerm(val original: String, val alternatives: Set<String>)

object QueryParser {

    fun parse(query: String): List<QueryTerm> =
        TextNormalizer.tokenize(query).map { token ->
            val alternatives = linkedSetOf<String>()
            HebrewPrefixes.variants(token).forEach { variant ->
                alternatives += variant
                alternatives += Synonyms.of(variant)
            }
            QueryTerm(token, alternatives)
        }
}

/**
 * Builds SQLite FTS4 `MATCH` expressions. Uses only the operators that both the standard and the
 * enhanced FTS query syntaxes support (implicit AND, `OR`, and `*` prefix), so it works whichever
 * one the device's SQLite was compiled with. In both syntaxes `OR` binds tighter than implicit
 * AND, so `a OR b c OR d` means (a OR b) AND (c OR d).
 *
 * Tokens contain only lowercase letters and digits, so they can't form uppercase operators such as
 * `OR`, `NOT` or `NEAR`, and need no escaping.
 */
object FtsQueryBuilder {

    /** Every query word must match (one of its alternatives). */
    fun matchAll(terms: List<QueryTerm>): String? = build(terms, joiner = " ")

    /** At least one query word must match. Used as a fallback when [matchAll] finds nothing. */
    fun matchAny(terms: List<QueryTerm>): String? = build(terms, joiner = " OR ")

    private fun build(terms: List<QueryTerm>, joiner: String): String? {
        if (terms.isEmpty()) return null
        return terms.withIndex().joinToString(joiner) { (i, term) ->
            // The last word may still be being typed, so it also matches as a prefix.
            val isLast = i == terms.lastIndex
            term.alternatives.joinToString(" OR ") { alt ->
                if (isLast && alt == term.original) "$alt*" else alt
            }
        }
    }
}
