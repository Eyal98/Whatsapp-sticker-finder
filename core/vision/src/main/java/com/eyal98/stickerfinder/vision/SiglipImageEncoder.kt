package com.eyal98.stickerfinder.vision

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import org.tensorflow.lite.Interpreter
import java.io.Closeable
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.sqrt

/**
 * Turns a sticker into a SigLIP2 image vector with the LiteRT interpreter, on the CPU. The same
 * preprocessing as tools/siglip/build_labels.py (which checked the model against the one the
 * label vectors came from): drawn on white, resized to 224 px, RGB scaled to [-1, 1].
 * Not thread-safe.
 */
class SiglipImageEncoder(model: ByteBuffer) : Closeable {

    private val interpreter = Interpreter(model, Interpreter.Options().setNumThreads(THREADS))
    private val inputShape = interpreter.getInputTensor(0).shape()
    private val channelsFirst = inputShape.contentEquals(intArrayOf(1, 3, SIZE, SIZE))
    val dim: Int = interpreter.getOutputTensor(0).shape().last()
    private val input = ByteBuffer.allocateDirect(4 * 3 * SIZE * SIZE).order(ByteOrder.nativeOrder())
    private val output = Array(1) { FloatArray(dim) }
    private val pixels = IntArray(SIZE * SIZE)

    init {
        require(channelsFirst || inputShape.contentEquals(intArrayOf(1, SIZE, SIZE, 3))) {
            "Unexpected SigLIP input shape ${inputShape.contentToString()}"
        }
    }

    /** Returns the L2-normalized image vector. */
    fun encode(sticker: Bitmap): FloatArray {
        val flat = Bitmap.createBitmap(sticker.width, sticker.height, Bitmap.Config.ARGB_8888)
        Canvas(flat).apply {
            drawColor(Color.WHITE)
            drawBitmap(sticker, 0f, 0f, null)
        }
        // Returns flat itself when it's already 224 px.
        val small = Bitmap.createScaledBitmap(flat, SIZE, SIZE, true)
        if (small !== flat) flat.recycle()
        try {
            small.getPixels(pixels, 0, SIZE, 0, 0, SIZE, SIZE)
        } finally {
            small.recycle()
        }

        input.rewind()
        val floats = input.asFloatBuffer()
        if (channelsFirst) {
            for (shift in intArrayOf(16, 8, 0)) {
                for (p in pixels) floats.put(scale((p shr shift) and 0xFF))
            }
        } else {
            for (p in pixels) {
                floats.put(scale((p shr 16) and 0xFF))
                floats.put(scale((p shr 8) and 0xFF))
                floats.put(scale(p and 0xFF))
            }
        }
        interpreter.run(input, output)

        val v = output[0].copyOf()
        var norm = 0f
        for (x in v) norm += x * x
        norm = sqrt(norm)
        if (norm > 0f) for (i in v.indices) v[i] /= norm
        return v
    }

    override fun close() {
        interpreter.close()
    }

    private companion object {
        const val SIZE = 224
        const val THREADS = 4

        fun scale(channel: Int): Float = (channel / 255f - 0.5f) / 0.5f
    }
}
