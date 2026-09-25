package com.eyal98.stickerfinder.search

import kotlin.math.min

/** Which language a test query is in; results are reported per language. */
enum class QueryLanguage {
    HEBREW,
    ENGLISH,

    /** Both scripts, or neither (emoji, numbers). */
    MIXED;

    companion object {
        fun detect(text: String): QueryLanguage {
            val hebrew = text.any { TextNormalizer.isHebrewLetter(it) }
            val latin = text.any { it in 'a'..'z' || it in 'A'..'Z' }
            return when {
                hebrew && !latin -> HEBREW
                latin && !hebrew -> ENGLISH
                else -> MIXED
            }
        }
    }
}

/**
 * One labelled test search. [relevant] holds sticker keys (the perceptual hash in hex), which stay
 * the same when a sticker file is renamed or rescanned.
 */
data class GoldenQuery(val id: String, val text: String, val relevant: Set<String>) {
    val language: QueryLanguage get() = QueryLanguage.detect(text)
}

/** Recall@5 and MRR@10 averaged over [count] queries. */
data class Score(val recallAt5: Double, val mrrAt10: Double, val count: Int)

object Metrics {

    /**
     * Share of the relevant stickers found in the top [k], out of as many as could fit there, so
     * a query with 8 right answers scores 1.0 when the top 5 are all right.
     */
    fun recallAt(ranked: List<String>, relevant: Set<String>, k: Int): Double {
        if (relevant.isEmpty()) return 0.0
        val found = ranked.take(k).count { it in relevant }
        return found.toDouble() / min(relevant.size, k)
    }

    /** 1 / rank of the first relevant result within the top [k], or 0 if none. */
    fun reciprocalRank(ranked: List<String>, relevant: Set<String>, k: Int): Double {
        val index = ranked.take(k).indexOfFirst { it in relevant }
        return if (index < 0) 0.0 else 1.0 / (index + 1)
    }

    /** Scores each query's ranking; the map has one entry per language present plus null for all. */
    fun score(runs: List<Pair<GoldenQuery, List<String>>>): Map<QueryLanguage?, Score> {
        fun of(subset: List<Pair<GoldenQuery, List<String>>>) = Score(
            recallAt5 = subset.map { (q, ranked) -> recallAt(ranked, q.relevant, 5) }.average(),
            mrrAt10 = subset.map { (q, ranked) -> reciprocalRank(ranked, q.relevant, 10) }.average(),
            count = subset.size,
        )
        if (runs.isEmpty()) return emptyMap()
        val byLanguage: Map<QueryLanguage?, Score> = runs.groupBy { it.first.language }.mapValues { of(it.value) }
        return byLanguage + (null to of(runs))
    }

    /** Value at quantile [q] (0..1) of [values], or 0 when empty. */
    fun percentile(values: List<Long>, q: Double): Long {
        if (values.isEmpty()) return 0
        val sorted = values.sorted()
        return sorted[((sorted.size - 1) * q).toInt()]
    }
}

/** Targets from the development plan (§8). */
object EvaluationTargets {
    const val MIN_RECALL_AT_5 = 0.80
    const val MAX_HEBREW_GAP = 0.05

    fun met(scores: Map<QueryLanguage?, Score>): Boolean {
        val all = scores[null] ?: return false
        val he = scores[QueryLanguage.HEBREW]
        val en = scores[QueryLanguage.ENGLISH]
        val gapOk = he == null || en == null || he.recallAt5 >= en.recallAt5 - MAX_HEBREW_GAP
        return all.recallAt5 >= MIN_RECALL_AT_5 && gapOk
    }
}
