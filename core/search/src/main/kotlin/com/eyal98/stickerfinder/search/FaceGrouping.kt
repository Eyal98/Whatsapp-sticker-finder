package com.eyal98.stickerfinder.search

import kotlin.random.Random

/**
 * Groups face vectors (SFace, L2-normalized) into people, by comparing faces with each other,
 * never with a group average: an average of different people's faces drifts toward a generic
 * face that looks close to everyone, which put almost every face in one group (build 133).
 *
 * - A new face joins an existing group through its single closest grouped face, if that face is
 *   close enough.
 * - The rest are linked to every other face that's close enough, and grouped with Chinese
 *   whispers: each face repeatedly takes the group most of its links point to. A group needs
 *   [MIN_GROUP] faces; the others stay ungrouped and are tried again later.
 */
object FaceGrouping {

    /**
     * Cosine similarity for "same person", between two faces. OpenCV's threshold for SFace is
     * 0.363 on photos; stickers are edited and small, so this is stricter.
     */
    const val SAME_PERSON = 0.5f

    const val MIN_GROUP = 2
    private const val ROUNDS = 20
    private const val MAX_LINKS = 10

    /**
     * Random pairs of faces are mostly different people and should score far below
     * [SAME_PERSON]. If their median is above this, the vectors can't tell people apart, and
     * grouping would only produce one big wrong group: skip it.
     */
    const val MAX_TYPICAL_SIMILARITY = 0.45f

    class Face(val id: Long, val vector: FloatArray)

    class Result(
        /** Face id to the existing group it joins. */
        val joined: Map<Long, Long>,
        /** New groups, as face ids. */
        val newGroups: List<List<Long>>,
    )

    /**
     * @param grouped faces already in a group, with the group's id.
     * @param ungrouped faces in no group that may be grouped (not ones the user took out).
     */
    fun group(grouped: List<Pair<Face, Long>>, ungrouped: List<Face>): Result {
        val joined = HashMap<Long, Long>()
        val rest = ArrayList<Face>()
        for (face in ungrouped) {
            var best: Long? = null
            var bestScore = SAME_PERSON
            for ((other, group) in grouped) {
                val score = Vectors.dot(face.vector, other.vector)
                if (score >= bestScore) {
                    best = group
                    bestScore = score
                }
            }
            if (best != null) joined[face.id] = best else rest += face
        }
        return Result(joined, whispers(rest))
    }

    private fun whispers(faces: List<Face>): List<List<Long>> {
        val n = faces.size
        // Each face keeps only its [MAX_LINKS] closest matches: memory stays small even if many
        // faces look alike (all-pairs links ran a 3,300-face collection out of memory).
        val neighbors = Array(n) { IntArray(0) }
        val weights = Array(n) { FloatArray(0) }
        val scores = FloatArray(n)
        for (i in 0 until n) {
            for (j in 0 until n) scores[j] = if (j == i) -1f else Vectors.dot(faces[i].vector, faces[j].vector)
            val top = (0 until n).filter { scores[it] >= SAME_PERSON }.sortedByDescending { scores[it] }.take(MAX_LINKS)
            neighbors[i] = top.toIntArray()
            weights[i] = FloatArray(top.size) { scores[top[it]] }
        }
        val label = IntArray(n) { it }
        val order = (0 until n).toMutableList()
        val random = Random(0)
        repeat(ROUNDS) {
            order.shuffle(random)
            var changed = false
            for (i in order) {
                if (neighbors[i].isEmpty()) continue
                val votes = HashMap<Int, Float>()
                for (k in neighbors[i].indices) {
                    val l = label[neighbors[i][k]]
                    votes[l] = (votes[l] ?: 0f) + weights[i][k]
                }
                val best = votes.maxByOrNull { it.value }!!.key
                if (best != label[i]) {
                    label[i] = best
                    changed = true
                }
            }
            if (!changed) return@repeat
        }
        return (0 until n).groupBy { label[it] }.values
            .filter { it.size >= MIN_GROUP }
            .map { members -> members.map { faces[it].id } }
    }

    /**
     * How similar random pairs of faces are, for the diagnostics report: median and 90th
     * percentile. Most pairs are different people, so a high median means the vectors don't
     * tell people apart (a broken model or preprocessing), not that thresholds are off.
     */
    fun pairStats(vectors: List<FloatArray>, pairs: Int = 2000): Pair<Float, Float>? {
        if (vectors.size < 2) return null
        val random = Random(1)
        val scores = FloatArray(pairs) {
            val a = random.nextInt(vectors.size)
            var b = random.nextInt(vectors.size - 1)
            if (b >= a) b++
            Vectors.dot(vectors[a], vectors[b])
        }.sorted()
        return scores[scores.size / 2] to scores[scores.size * 9 / 10]
    }
}
