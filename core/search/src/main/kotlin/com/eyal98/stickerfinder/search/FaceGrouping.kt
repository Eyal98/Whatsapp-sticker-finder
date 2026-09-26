package com.eyal98.stickerfinder.search

import kotlin.math.sqrt

/**
 * Groups face vectors (SFace, L2-normalized) into people. Groups the user already has are kept:
 * a new face joins the closest existing group if it's close enough; the rest are grouped among
 * themselves ("leader" clustering on running centroids), and a group needs [MIN_GROUP] faces to
 * be created. Faces that match nobody stay ungrouped and are tried again next time.
 */
object FaceGrouping {

    /**
     * Cosine similarity for "same person". OpenCV's threshold for SFace is 0.363 on photos;
     * stickers are cut out, filtered and small, so this is stricter: a missed match costs a tap
     * (merge), a wrong one puts someone else's name on a sticker.
     */
    const val SAME_PERSON = 0.42f

    const val MIN_GROUP = 2

    class Face(val id: Long, val vector: FloatArray)

    class Result(
        /** Face id to the existing group it joins. */
        val joined: Map<Long, Long>,
        /** New groups, as face ids. */
        val newGroups: List<List<Long>>,
    )

    /**
     * @param groups existing groups: group id to its faces' vectors.
     * @param ungrouped faces in no group that may be grouped (not ones the user took out).
     */
    fun group(groups: Map<Long, List<FloatArray>>, ungrouped: List<Face>): Result {
        val centroids = groups.mapNotNull { (id, vectors) -> centroid(vectors)?.let { id to it } }
        val joined = HashMap<Long, Long>()
        val rest = ArrayList<Face>()
        for (face in ungrouped) {
            val best = centroids.maxByOrNull { Vectors.dot(it.second, face.vector) }
            if (best != null && Vectors.dot(best.second, face.vector) >= SAME_PERSON) {
                joined[face.id] = best.first
            } else {
                rest += face
            }
        }

        // Leader clustering: each face joins the closest cluster whose centroid is close enough,
        // or starts a new one. Then one more pass reassigns every face to its closest centroid,
        // which undoes most of the order dependence.
        val sums = ArrayList<FloatArray>()
        val members = ArrayList<MutableList<Face>>()
        fun closest(v: FloatArray): Int {
            var best = -1
            var bestScore = SAME_PERSON
            for (i in sums.indices) {
                val score = cosine(sums[i], v)
                if (score >= bestScore) {
                    best = i
                    bestScore = score
                }
            }
            return best
        }
        for (face in rest) {
            val i = closest(face.vector)
            if (i < 0) {
                sums += face.vector.copyOf()
                members += mutableListOf(face)
            } else {
                add(sums[i], face.vector)
                members[i] += face
            }
        }
        val finalMembers = List(sums.size) { mutableListOf<Long>() }
        for (face in rest) {
            val i = closest(face.vector)
            if (i >= 0) finalMembers[i] += face.id
        }
        return Result(joined, finalMembers.filter { it.size >= MIN_GROUP })
    }

    private fun centroid(vectors: List<FloatArray>): FloatArray? {
        if (vectors.isEmpty()) return null
        val sum = vectors.first().copyOf()
        for (v in vectors.drop(1)) add(sum, v)
        return normalized(sum)
    }

    private fun add(into: FloatArray, v: FloatArray) {
        for (i in into.indices) into[i] += v[i]
    }

    private fun cosine(sum: FloatArray, v: FloatArray): Float {
        var norm = 0f
        for (x in sum) norm += x * x
        return if (norm == 0f) 0f else Vectors.dot(sum, v) / sqrt(norm)
    }

    private fun normalized(v: FloatArray): FloatArray {
        var norm = 0f
        for (x in v) norm += x * x
        val n = sqrt(norm)
        return if (n == 0f) v else FloatArray(v.size) { v[it] / n }
    }
}
