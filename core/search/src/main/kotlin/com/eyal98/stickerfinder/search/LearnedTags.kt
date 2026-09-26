package com.eyal98.stickerfinder.search

import kotlin.math.sqrt
import kotlin.random.Random

/**
 * Learns what the user's own tags look like, so they can be suggested on look-alike stickers. The
 * picture model only knows a fixed label list; a tag like "Kermit", a friend's name or a local
 * show is learned here from the stickers the user already gave it.
 *
 * For each tag, the picture vectors of the stickers that have it are averaged into a prototype.
 * A sticker without the tag gets it suggested when its vector is close enough to the prototype:
 * - The tag has to be visual. If its examples look nothing alike ("funny", "morning"), their
 *   prototype means nothing and the tag is skipped.
 * - "Close enough" adapts to the tag and to the model: at least as close as the tag's own least
 *   typical example (leave-one-out, minus a small margin), and clearly closer than random pairs of
 *   stickers are to each other.
 * - A tag on a single sticker only spreads to near-copies of it.
 */
object LearnedTags {

    /** A tag the prototype matched on a sticker, with how close it was. */
    data class Suggestion(val tag: String, val similarity: Float)

    /** What was learned, for diagnostics (numbers only). */
    data class Stats(
        val tags: Int,
        val learnedTags: Int,
        val suggestions: Int,
        /** How alike two random stickers typically are, and the 99th percentile. */
        val randomMedian: Float,
        val randomP99: Float,
    )

    data class Result(val suggestions: Map<Long, List<Suggestion>>, val stats: Stats)

    /** Below this many examples the tag only spreads to near-copies. */
    const val MIN_EXAMPLES = 2

    /** Examples of a visual tag are at least this alike, on average, to their prototype. */
    const val MIN_COHERENCE = 0.6f

    /** A one-example tag spreads only to stickers at least this similar (near-copies). */
    const val SINGLE_EXAMPLE_SIMILARITY = 0.9f

    /** How far below the least typical example a match may be. */
    const val MARGIN = 0.03f

    /** How far above random pairs' 99th percentile a match has to be. */
    const val ABOVE_RANDOM = 0.05f

    /** Never suggest below this, whatever the other rules say. */
    const val FLOOR = 0.6f

    const val MAX_PER_TAG = 40
    const val MAX_PER_STICKER = 3

    private const val RANDOM_PAIRS = 3000

    /**
     * @param vectors each sticker's picture vector; scaled to unit length in place (thousands of
     *   picture vectors are tens of MB, so they aren't copied)
     * @param tags each sticker's own tags
     * @param blocked tags not to suggest on a sticker (lower case): ones the user hid there
     * @return suggestions per sticker, best first; stickers with none are left out
     */
    fun suggest(
        vectors: Map<Long, FloatArray>,
        tags: Map<Long, List<String>>,
        blocked: Map<Long, Set<String>> = emptyMap(),
        random: Random = Random(7),
    ): Result {
        vectors.values.forEach(::normalizeInPlace)
        val unit = vectors
        val ids = unit.keys.toList()
        val (randomMedian, randomP99) = randomPairs(ids.map { unit.getValue(it) }, random)
        val floor = maxOf(FLOOR, randomP99 + ABOVE_RANDOM)

        // Tags by lower case, keeping the spelling used most.
        val byTag = HashMap<String, MutableList<Long>>()
        val spelling = HashMap<String, MutableMap<String, Int>>()
        for ((id, list) in tags) {
            if (id !in unit) continue
            for (tag in list) {
                val key = tag.trim().lowercase()
                if (key.isEmpty()) continue
                byTag.getOrPut(key) { mutableListOf() } += id
                spelling.getOrPut(key) { HashMap() }.merge(tag.trim(), 1) { a, b -> a + b }
            }
        }

        val found = HashMap<Long, MutableList<Suggestion>>()
        var learned = 0
        for ((key, examples) in byTag) {
            val display = spelling.getValue(key).maxBy { it.value }.key
            val threshold = if (examples.size < MIN_EXAMPLES) {
                SINGLE_EXAMPLE_SIMILARITY
            } else {
                val exampleVectors = examples.map { unit.getValue(it) }
                val prototype = mean(exampleVectors)
                if (exampleVectors.map { Vectors.dot(it, prototype) }.average() < MIN_COHERENCE) continue
                // The least typical example, measured against the others only (their sum is the
                // total minus itself).
                val total = sum(exampleVectors)
                val leaveOneOut = exampleVectors.minOf { e ->
                    Vectors.dot(e, normalized(FloatArray(total.size) { total[it] - e[it] }))
                }
                maxOf(floor, leaveOneOut - MARGIN)
            }
            val prototype = mean(examples.map { unit.getValue(it) })
            val exampleSet = examples.toHashSet()
            val matches = ids.asSequence()
                .filter { it !in exampleSet && key !in blocked[it].orEmpty() }
                .map { it to Vectors.dot(unit.getValue(it), prototype) }
                .filter { it.second >= threshold }
                .sortedByDescending { it.second }
                .take(MAX_PER_TAG)
                .toList()
            if (matches.isEmpty()) continue
            learned++
            for ((id, similarity) in matches) found.getOrPut(id) { mutableListOf() } += Suggestion(display, similarity)
        }

        val suggestions = found.mapValues { (_, list) -> list.sortedByDescending { it.similarity }.take(MAX_PER_STICKER) }
        return Result(
            suggestions,
            Stats(byTag.size, learned, suggestions.values.sumOf { it.size }, randomMedian, randomP99),
        )
    }

    private fun normalizeInPlace(v: FloatArray) {
        var norm = 0.0
        for (x in v) norm += x * x
        val length = sqrt(norm).toFloat()
        if (length > 0f) for (i in v.indices) v[i] /= length
    }

    private fun normalized(v: FloatArray): FloatArray {
        var norm = 0.0
        for (x in v) norm += x * x
        val length = sqrt(norm).toFloat()
        return if (length > 0f) FloatArray(v.size) { v[it] / length } else v.copyOf()
    }

    private fun sum(vectors: List<FloatArray>): FloatArray {
        val sum = FloatArray(vectors.first().size)
        for (v in vectors) for (i in sum.indices) sum[i] += v[i]
        return sum
    }

    /** The direction of the average (unit length). */
    private fun mean(vectors: List<FloatArray>): FloatArray = normalized(sum(vectors))

    /** Median and 99th percentile similarity of random pairs: how alike unrelated stickers are. */
    private fun randomPairs(vectors: List<FloatArray>, random: Random): Pair<Float, Float> {
        if (vectors.size < 2) return 0f to 0f
        val sims = FloatArray(RANDOM_PAIRS) {
            val a = random.nextInt(vectors.size)
            var b = random.nextInt(vectors.size - 1)
            if (b >= a) b++
            Vectors.dot(vectors[a], vectors[b])
        }
        sims.sort()
        return sims[sims.size / 2] to sims[(sims.size * 99) / 100]
    }
}
