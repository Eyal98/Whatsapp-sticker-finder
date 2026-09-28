package com.eyal98.stickerfinder.search

/**
 * Puts restored names back on the new phone's face groups. A backup keeps, for each named person,
 * the average of their face vectors; the new phone groups faces by itself, and a group gets a
 * name when its own average is clearly that person's: close enough, and clearly closer than to
 * anyone else saved (two people who look alike are left for the user to name).
 */
object PeopleNames {

    /** Averages of the same person's faces are at least this alike (SFace, cosine). */
    const val SAME_PERSON = 0.5f

    /** How much closer the best saved person must be than the next one. */
    const val MARGIN = 0.05f

    /** The unit-length average of [vectors], or null if there are none. */
    fun centroid(vectors: List<FloatArray>): FloatArray? {
        if (vectors.isEmpty()) return null
        val sum = FloatArray(vectors.first().size)
        for (v in vectors) for (i in sum.indices) sum[i] += v.getOrElse(i) { 0f }
        return Vectors.prepare(sum, sum.size)
    }

    /**
     * Names for [groups] (group id to its centroid) from [saved] (name to centroid): only groups
     * whose best saved person is at least [SAME_PERSON] alike and [MARGIN] ahead of the next.
     */
    fun match(groups: Map<Long, FloatArray>, saved: List<Pair<String, FloatArray>>): Map<Long, String> =
        groups.mapNotNull { (group, centroid) ->
            val ranked = saved.map { (name, c) -> name to Vectors.dot(centroid, c) }.sortedByDescending { it.second }
            val best = ranked.firstOrNull() ?: return@mapNotNull null
            val next = ranked.getOrNull(1)?.second
            if (best.second >= SAME_PERSON && (next == null || best.second - next >= MARGIN)) group to best.first else null
        }.toMap()
}
