package com.eyal98.stickerfinder.data

import com.eyal98.stickerfinder.search.EvaluationTargets
import com.eyal98.stickerfinder.search.GoldenQuery
import com.eyal98.stickerfinder.search.Metrics
import com.eyal98.stickerfinder.search.QueryLanguage
import com.eyal98.stickerfinder.search.Score

enum class Ranker { KEYWORD, MEANING, COMBINED }

data class EvaluationReport(
    val scores: Map<Ranker, Map<QueryLanguage?, Score>>,
    val latencyP50Ms: Long,
    val latencyP95Ms: Long,
    /** The similarity cut-off search currently uses. */
    val threshold: Float,
    /** Combined overall score at each candidate cut-off. */
    val sweep: List<Pair<Float, Score>>,
    /** The cut-off with the best combined score, or null without a meaning model. */
    val bestThreshold: Float?,
    /** Test searches whose top 5 (combined) missed at least one right sticker. */
    val misses: List<String>,
    /** Test searches skipped because none of their stickers are on this phone any more. */
    val skipped: Int,
    val semanticAvailable: Boolean,
) {
    val targetsMet: Boolean get() = EvaluationTargets.met(scores[Ranker.COMBINED].orEmpty())
}

/**
 * Runs the labelled test searches through the real search pipeline (same database, same models)
 * and measures how often the right stickers come up.
 */
class SearchEvaluator(
    private val dao: StickerDao,
    private val repository: StickerRepository,
    private val semantic: SemanticSearch,
    private val settings: SearchSettings,
) {

    suspend fun run(queries: List<GoldenQuery>, onProgress: (done: Int, total: Int) -> Unit): EvaluationReport {
        val stickers = dao.allStickers().associateBy { it.id }
        val present = stickers.values.mapTo(HashSet(), StickerRepository::imageKey)
        val usable = queries
            .map { it.copy(relevant = it.relevant intersect present) }
            .filter { it.relevant.isNotEmpty() }
        val threshold = settings.minSimilarity

        fun keys(ids: List<Long>) = ids.mapNotNull(stickers::get).map(StickerRepository::imageKey).distinct()

        val keywordRuns = mutableListOf<Pair<GoldenQuery, List<String>>>()
        val meaningRuns = mutableListOf<Pair<GoldenQuery, List<String>>>()
        val combinedRuns = mutableListOf<Pair<GoldenQuery, List<String>>>()
        val sweepRuns = SWEEP.associateWith { mutableListOf<Pair<GoldenQuery, List<String>>>() }
        val latencies = mutableListOf<Long>()
        var semanticAvailable = false

        // The first search loads the embedding model; keep that out of the timings.
        usable.firstOrNull()?.let { repository.search(it.text) }

        usable.forEachIndexed { i, q ->
            onProgress(i, usable.size)
            val start = System.nanoTime()
            repository.search(q.text)
            latencies += (System.nanoTime() - start) / 1_000_000

            val keyword = repository.searchKeywords(q.text).map { it.id }
            // Scored once with the lowest candidate cut-off; each cut-off is then a filter.
            val scored = semantic.searchScored(q.text, SemanticSearch.LIMIT, SWEEP.first())
            if (scored.isNotEmpty()) semanticAvailable = true
            fun meaningAt(t: Float) = scored.filter { it.second >= t }.map { it.first }

            keywordRuns += q to keys(keyword)
            meaningRuns += q to keys(meaningAt(threshold))
            combinedRuns += q to keys(StickerRepository.fuse(keyword, meaningAt(threshold), stickers))
            for (t in SWEEP) sweepRuns.getValue(t) += q to keys(StickerRepository.fuse(keyword, meaningAt(t), stickers))
        }
        onProgress(usable.size, usable.size)

        val sweep = SWEEP.mapNotNull { t -> Metrics.score(sweepRuns.getValue(t))[null]?.let { t to it } }
        return EvaluationReport(
            scores = mapOf(
                Ranker.KEYWORD to Metrics.score(keywordRuns),
                Ranker.MEANING to Metrics.score(meaningRuns),
                Ranker.COMBINED to Metrics.score(combinedRuns),
            ),
            latencyP50Ms = Metrics.percentile(latencies, 0.5),
            latencyP95Ms = Metrics.percentile(latencies, 0.95),
            threshold = threshold,
            sweep = sweep,
            bestThreshold = if (semanticAvailable) {
                sweep.maxWithOrNull(compareBy({ it.second.recallAt5 }, { it.second.mrrAt10 }))?.first
            } else {
                null
            },
            misses = combinedRuns.filter { (q, ranked) -> Metrics.recallAt(ranked, q.relevant, 5) < 1.0 }.map { it.first.text },
            skipped = queries.size - usable.size,
            semanticAvailable = semanticAvailable,
        )
    }

    companion object {
        /** Candidate similarity cut-offs, lowest first. */
        val SWEEP = listOf(0.15f, 0.2f, 0.25f, 0.3f, 0.35f, 0.4f, 0.45f, 0.5f, 0.55f, 0.6f)
    }
}
