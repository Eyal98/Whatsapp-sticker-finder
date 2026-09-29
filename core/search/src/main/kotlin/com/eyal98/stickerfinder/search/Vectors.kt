package com.eyal98.stickerfinder.search

import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.sqrt

object Vectors {

    /**
     * Stored vector size. EmbeddingGemma is trained so its first dimensions carry most of the
     * meaning (Matryoshka), so 256 of its 768 keep search quality while cutting memory by 3x.
     */
    const val DIMENSIONS = 256

    /** Truncates to [dims] and scales to unit length, so a dot product is the cosine similarity. */
    fun prepare(raw: FloatArray, dims: Int = DIMENSIONS): FloatArray {
        val v = raw.copyOf(minOf(dims, raw.size))
        var norm = 0.0
        for (x in v) norm += x * x
        val length = sqrt(norm).toFloat()
        if (length > 0f) for (i in v.indices) v[i] /= length
        return v
    }

    fun dot(a: FloatArray, b: FloatArray): Float {
        var sum = 0f
        for (i in 0 until minOf(a.size, b.size)) sum += a[i] * b[i]
        return sum
    }

    fun encode(v: FloatArray): ByteArray {
        val buffer = ByteBuffer.allocate(v.size * 4).order(ByteOrder.LITTLE_ENDIAN)
        v.forEach(buffer::putFloat)
        return buffer.array()
    }

    fun decode(bytes: ByteArray): FloatArray {
        val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        return FloatArray(bytes.size / 4) { buffer.getFloat() }
    }
}

/**
 * All meaning vectors in memory, for brute-force search. Tens of thousands of vectors × 768
 * dimensions as floats is over 60 MB (and twice that while loading), too much for an app's heap;
 * each vector is kept as bytes instead (8-bit values with one scale per vector): a quarter of the
 * memory, and similarities within about 1% of the exact ones, far below what changes a ranking.
 * Add vectors with [Builder] a page at a time.
 */
class MeaningIndex private constructor(
    private val stickerIds: LongArray,
    private val packs: IntArray,
    private val codes: ByteArray,
    private val scales: FloatArray,
    private val dims: Int,
) {
    /** Vectors held (a sticker can have several: one per [Facet], and one learned from chats). */
    val size: Int get() = stickerIds.size

    class Builder(private val dims: Int) {
        private var ids = LongArray(256)
        private var packs = IntArray(256)
        private var codes = ByteArray(256 * dims)
        private var scales = FloatArray(256)
        private var n = 0

        /**
         * Adds one of [stickerId]'s vectors ([Vectors.prepare]d); [pack] groups stickers of one pack
         * (-1: none). Its similarities are multiplied by [weight], to favor one kind of vector a
         * little.
         */
        fun add(stickerId: Long, vector: FloatArray, pack: Int, weight: Float = 1f) {
            if (vector.size != dims) return
            if (n == ids.size) {
                val grown = ids.size * 2
                ids = ids.copyOf(grown)
                packs = packs.copyOf(grown)
                scales = scales.copyOf(grown)
                codes = codes.copyOf(grown * dims)
            }
            var maxAbs = 0f
            for (x in vector) maxAbs = maxOf(maxAbs, kotlin.math.abs(x))
            val scale = if (maxAbs == 0f) 1f else maxAbs / 127f
            val offset = n * dims
            for (i in 0 until dims) codes[offset + i] = kotlin.math.round(vector[i] / scale).toInt().toByte()
            ids[n] = stickerId
            packs[n] = pack
            scales[n] = scale * weight
            n++
        }

        fun build() = MeaningIndex(ids.copyOf(n), packs.copyOf(n), codes.copyOf(n * dims), scales.copyOf(n), dims)
    }

    /** Every sticker's similarity to [query]: the best of its vectors. */
    fun scores(query: FloatArray): List<MeaningSelection.Scored> {
        if (query.size != dims) return emptyList()
        val best = LinkedHashMap<Long, MeaningSelection.Scored>(stickerIds.size)
        for (row in stickerIds.indices) {
            val offset = row * dims
            var sum = 0f
            for (i in 0 until dims) sum += query[i] * codes[offset + i]
            val similarity = sum * scales[row]
            val id = stickerIds[row]
            val previous = best[id]
            if (previous == null || similarity > previous.similarity) best[id] = MeaningSelection.Scored(id, similarity, packs[row])
        }
        return best.values.toList()
    }
}

/**
 * Which stickers count as meaning matches for a search. A fixed similarity cut-off can't work:
 * how similar unrelated stickers look depends on the search, so a vague search had a hundred
 * weak "matches" crowding the results. Instead a sticker has to stand out from all the others for
 * this search (a robust z-score: distance from the median in units of the spread), clear the
 * user's minimum similarity, and no pack may fill the list.
 */
object MeaningSelection {

    class Scored(val stickerId: Long, val similarity: Float, val pack: Int)

    /** How far above the typical sticker a match must be, in robust standard deviations. */
    const val MIN_Z = 3.0f

    /** At most this many stickers from one pack: a pack's look-alikes shouldn't fill the list. */
    const val MAX_PER_PACK = 5

    /** Below this many stickers the spread isn't meaningful: only the minimum similarity applies. */
    private const val MIN_FOR_STATS = 30

    fun select(scores: List<Scored>, minSimilarity: Float, limit: Int, minZ: Float = MIN_Z): List<Pair<Long, Float>> {
        val cutOff = if (scores.size < MIN_FOR_STATS) {
            minSimilarity
        } else {
            val sorted = FloatArray(scores.size) { scores[it].similarity }.apply { sort() }
            val median = sorted[sorted.size / 2]
            val deviations = FloatArray(sorted.size) { kotlin.math.abs(sorted[it] - median) }.apply { sort() }
            // 1.4826 × the median absolute deviation estimates the standard deviation, without
            // letting a big group of real matches (every cat sticker for "cat") inflate it.
            val spread = 1.4826f * deviations[deviations.size / 2]
            maxOf(minSimilarity, median + minZ * spread)
        }
        val perPack = HashMap<Int, Int>()
        return scores.asSequence()
            .filter { it.similarity >= cutOff }
            .sortedByDescending { it.similarity }
            .filter { s -> s.pack < 0 || perPack.merge(s.pack, 1, Int::plus)!! <= MAX_PER_PACK }
            .take(limit)
            .map { it.stickerId to it.similarity }
            .toList()
    }
}
