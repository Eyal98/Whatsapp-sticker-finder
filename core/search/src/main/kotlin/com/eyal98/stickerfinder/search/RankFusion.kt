package com.eyal98.stickerfinder.search

/**
 * Reciprocal Rank Fusion: combines several ranked result lists (keyword, semantic, ...) into one
 * without needing their scores to be comparable. score(d) = Σ 1 / (k + rank(d)) over each list,
 * with rank starting at 1.
 */
object RankFusion {

    const val DEFAULT_K = 60

    /**
     * @param rankings ranked lists of sticker ids, best first.
     * @param boosts extra score per id, e.g. for starred or often-used stickers.
     * @return ids ordered by fused score, best first. Ties keep first-seen order.
     */
    fun <T> fuse(
        rankings: List<List<T>>,
        boosts: Map<T, Double> = emptyMap(),
        k: Int = DEFAULT_K,
    ): List<T> {
        val scores = LinkedHashMap<T, Double>()
        for (ranking in rankings) {
            ranking.forEachIndexed { index, id ->
                scores[id] = (scores[id] ?: 0.0) + 1.0 / (k + index + 1)
            }
        }
        for ((id, boost) in boosts) {
            scores.computeIfPresent(id) { _, score -> score + boost }
        }
        return scores.entries.sortedByDescending { it.value }.map { it.key }
    }
}
