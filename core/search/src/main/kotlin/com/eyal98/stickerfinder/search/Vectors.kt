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
 * In-memory nearest-neighbour search by brute force. Thousands of stickers × 256 dimensions is a
 * few million multiply-adds per query: a few milliseconds, with no index to build or corrupt.
 */
class VectorIndex(private val entries: List<Pair<Long, FloatArray>>) {

    val size: Int get() = entries.size

    /**
     * Returns up to [limit] ids ordered by similarity to [query] (already [Vectors.prepare]d),
     * keeping only those at least [minSimilarity] similar.
     */
    fun search(query: FloatArray, limit: Int, minSimilarity: Float): List<Pair<Long, Float>> =
        entries.asSequence()
            .map { (id, v) -> id to Vectors.dot(query, v) }
            .filter { it.second >= minSimilarity }
            .sortedByDescending { it.second }
            .take(limit)
            .toList()
}
