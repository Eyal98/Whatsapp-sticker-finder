package com.eyal98.stickerfinder.vision

import android.content.Context
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest

/**
 * The fixed picture-tag vocabulary and its SigLIP2 text vectors, built offline by
 * tools/siglip/build_labels.py. A sticker's tags are the labels whose vectors are closest to its
 * image vector (see [pick]).
 */
class PictureLabels private constructor(
    val count: Int,
    val dim: Int,
    /** [count] rows of [dim] floats, each L2-normalized. */
    private val vectors: FloatArray,
    private val tags: List<List<String>>,
    /** Identifies this label list; stored with each sticker's tags. */
    val version: String,
) {

    /** Cosine similarity of [image] (L2-normalized, [dim] floats) to every label. */
    fun similarities(image: FloatArray): FloatArray {
        require(image.size == dim) { "Expected a $dim-float vector, got ${image.size}" }
        val out = FloatArray(count)
        for (row in 0 until count) {
            var sum = 0f
            val base = row * dim
            for (i in 0 until dim) sum += vectors[base + i] * image[i]
            out[row] = sum
        }
        return out
    }

    /** Comma-separated English and Hebrew tags for [image], or "" when no label fits. */
    fun tagsFor(image: FloatArray): String =
        pick(similarities(image)).flatMap { tags[it] }.distinct().joinToString(", ")

    companion object {
        private const val ASSET_TSV = "siglip/labels.tsv"
        private const val ASSET_BIN = "siglip/labels.bin"
        private val MAGIC = "SLB1".toByteArray(Charsets.US_ASCII)
        private const val HEADER_BYTES = 4 + 4 + 4 + 4 + 4 + 32

        /** At most this many labels per sticker. */
        const val MAX_LABELS = 5

        /** Below this similarity a label is never used: nothing in the list fits the sticker. */
        const val MIN_SIMILARITY = 0.08f

        /** Labels well behind the best match are dropped; they're usually unrelated. */
        const val MAX_GAP = 0.025f

        /** The labels to tag a sticker with, best first. Pure, for tests. */
        fun pick(similarities: FloatArray): List<Int> {
            val order = similarities.indices.sortedByDescending { similarities[it] }
            val best = order.firstOrNull()?.let { similarities[it] } ?: return emptyList()
            return order.asSequence()
                .take(MAX_LABELS)
                .filter { similarities[it] >= MIN_SIMILARITY && similarities[it] >= best - MAX_GAP }
                .toList()
        }

        fun load(context: Context): PictureLabels {
            val tsv = context.assets.open(ASSET_TSV).use { String(it.readBytes(), Charsets.UTF_8) }
            val bin = context.assets.open(ASSET_BIN).use { it.readBytes() }
            return parse(tsv, bin)
        }

        /** Parses labels.tsv and the matching vector file; fails if they don't belong together. */
        fun parse(tsv: String, bin: ByteArray): PictureLabels {
            val rows = tsv.lineSequence()
                .filter { it.isNotBlank() && !it.startsWith("#") }
                .map { line ->
                    val cols = line.split('\t')
                    require(cols.size == 3) { "Bad labels.tsv line: $line" }
                    cols
                }
                .toList()
            val prompts = rows.map { it[0].trim() }
            val tags = rows.map { cols -> (splitTags(cols[1]) + splitTags(cols[2])).distinct() }

            val buffer = ByteBuffer.wrap(bin).order(ByteOrder.LITTLE_ENDIAN)
            require(bin.size >= HEADER_BYTES) { "Label vector file is too short" }
            val magic = ByteArray(4).also { buffer.get(it) }
            require(magic.contentEquals(MAGIC)) { "Not a label vector file" }
            val count = buffer.int
            val dim = buffer.int
            buffer.float // logit scale: unused, tags are picked by similarity
            buffer.float // logit bias
            val promptHash = ByteArray(32).also { buffer.get(it) }
            require(count == prompts.size) { "labels.tsv has ${prompts.size} labels, vectors have $count" }
            val expectedHash = MessageDigest.getInstance("SHA-256")
                .digest(prompts.joinToString("\n").toByteArray(Charsets.UTF_8))
            require(promptHash.contentEquals(expectedHash)) { "labels.tsv doesn't match the label vectors" }
            require(bin.size == HEADER_BYTES + count * dim * 2) { "Label vector file has the wrong size" }

            val vectors = FloatArray(count * dim) { halfToFloat(buffer.short) }
            return PictureLabels(count, dim, vectors, tags, promptHash.toHex().take(16))
        }

        private fun splitTags(column: String) = column.split(',').map { it.trim() }.filter { it.isNotEmpty() }

        private fun ByteArray.toHex() = joinToString("") { "%02x".format(it) }

        /** IEEE 754 half precision to float (android.util.Half isn't available in unit tests). */
        internal fun halfToFloat(half: Short): Float {
            val h = half.toInt() and 0xFFFF
            val sign = (h ushr 15) shl 31
            val exponent = (h ushr 10) and 0x1F
            val mantissa = h and 0x3FF
            val bits = when {
                exponent == 0 && mantissa == 0 -> sign
                exponent == 0 -> {
                    // Subnormal: normalize it.
                    var m = mantissa
                    var e = -14
                    while (m and 0x400 == 0) {
                        m = m shl 1
                        e--
                    }
                    sign or ((e + 127) shl 23) or ((m and 0x3FF) shl 13)
                }
                exponent == 0x1F -> sign or (0xFF shl 23) or (mantissa shl 13)
                else -> sign or ((exponent - 15 + 127) shl 23) or (mantissa shl 13)
            }
            return Float.fromBits(bits)
        }
    }
}
