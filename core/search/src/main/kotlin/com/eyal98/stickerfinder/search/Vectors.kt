package com.eyal98.stickerfinder.search

import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs
import kotlin.math.roundToInt
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
 * Many vectors in one compact block, for comparing a query with all of them by brute force. Each
 * is scaled to unit length and stored as signed bytes with its own scale (8-bit quantization): a
 * quarter of the memory of float arrays, with no per-vector object. 10,000 stickers × 768
 * dimensions is under 8 MB instead of 31 MB (plus as much again while loading), which, together
 * with other passes, ran a 256 MB heap out. A similarity is off by about 0.001 at most, far below
 * any cut-off that uses it.
 *
 * Filled with [add] (or [addEncoded], straight from the stored bytes); not thread-safe while
 * filling, read-only after.
 */
class PackedVectors(val dims: Int, capacity: Int = 16) {
    private var ids = LongArray(capacity.coerceAtLeast(1))
    private var codes = ByteArray(ids.size * dims)
    private var scales = FloatArray(ids.size)
    private val scratch = FloatArray(dims)

    var size: Int = 0
        private set

    fun id(index: Int): Long = ids[index]

    /** Adds [vector], scaled to unit length. A vector of another size is skipped: returns false. */
    fun add(id: Long, vector: FloatArray): Boolean {
        if (vector.size != dims) return false
        vector.copyInto(scratch)
        append(id)
        return true
    }

    /** Adds a vector stored by [Vectors.encode], without decoding it to a new array first. */
    fun addEncoded(id: Long, bytes: ByteArray): Boolean {
        if (bytes.size != dims * 4) return false
        val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        for (i in 0 until dims) scratch[i] = buffer.getFloat()
        append(id)
        return true
    }

    private fun append(id: Long) {
        if (size == ids.size) grow()
        var norm = 0.0
        for (x in scratch) norm += x * x
        val length = sqrt(norm).toFloat()
        var max = 0f
        for (x in scratch) max = maxOf(max, abs(x))
        // Unit length, then the largest component maps to ±127.
        val scale = if (length > 0f && max > 0f) max / length / 127f else 0f
        val base = size * dims
        for (i in 0 until dims) {
            val code = if (scale > 0f) (scratch[i] / length / scale).roundToInt().coerceIn(-127, 127) else 0
            codes[base + i] = code.toByte()
        }
        ids[size] = id
        scales[size] = scale
        size++
    }

    private fun grow() {
        val capacity = ids.size * 2
        ids = ids.copyOf(capacity)
        codes = codes.copyOf(capacity * dims)
        scales = scales.copyOf(capacity)
    }

    /** Frees the room reserved for vectors that weren't added. */
    fun trim() {
        if (size == ids.size || size == 0) return
        ids = ids.copyOf(size)
        codes = codes.copyOf(size * dims)
        scales = scales.copyOf(size)
    }

    /** Cosine similarity of vector [index] with [query] (unit length, [dims] floats). */
    fun dot(index: Int, query: FloatArray): Float {
        val base = index * dims
        var sum = 0f
        for (i in 0 until minOf(dims, query.size)) sum += codes[base + i] * query[i]
        return sum * scales[index]
    }

    /** Cosine similarity of vectors [a] and [b]. */
    fun dot(a: Int, b: Int): Float {
        val baseA = a * dims
        val baseB = b * dims
        var sum = 0
        for (i in 0 until dims) sum += codes[baseA + i] * codes[baseB + i]
        return sum * scales[a] * scales[b]
    }

    /** Vector [index] as floats (unit length, give or take the rounding). */
    fun vector(index: Int): FloatArray {
        val base = index * dims
        val scale = scales[index]
        return FloatArray(dims) { codes[base + it] * scale }
    }

    /**
     * Up to [limit] ids ordered by similarity to [query] (already [Vectors.prepare]d), keeping
     * only those at least [minSimilarity] similar.
     */
    fun search(query: FloatArray, limit: Int, minSimilarity: Float): List<Pair<Long, Float>> {
        val scores = FloatArray(size) { dot(it, query) }
        return (0 until size).filter { scores[it] >= minSimilarity }
            .sortedByDescending { scores[it] }
            .take(limit)
            .map { ids[it] to scores[it] }
    }
}
