package com.eyal98.stickerfinder.vision

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.PointF
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import com.google.mlkit.vision.face.FaceLandmark
import org.tensorflow.lite.Interpreter
import java.io.Closeable
import java.io.FileInputStream
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel
import kotlin.math.sqrt

/** A face found on a sticker: its box (fractions of the sticker's size) and identity vector. */
class FoundFace(val x0: Float, val y0: Float, val x1: Float, val y1: Float, val vector: FloatArray)

/**
 * Finds faces on stickers and turns each into an identity vector: ML Kit's bundled face detector
 * finds faces and their landmarks, each face is aligned to 112x112, and SFace (bundled, see
 * core/vision/faces.properties) gives a 128-d vector; the same person's faces have close
 * vectors. Everything runs on the phone. Not thread-safe; call from a background thread.
 */
class StickerFaces(model: ByteBuffer) : Closeable {

    private val detector = FaceDetection.getClient(
        FaceDetectorOptions.Builder()
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_ACCURATE)
            .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_ALL)
            .setMinFaceSize(MIN_FACE_FRACTION)
            .build(),
    )
    private val interpreter = Interpreter(model, Interpreter.Options().setNumThreads(2))
    private val input = ByteBuffer.allocateDirect(4 * 3 * FaceAlign.SIZE * FaceAlign.SIZE).order(ByteOrder.nativeOrder())
    private val output = Array(1) { FloatArray(interpreter.getOutputTensor(0).shape().last()) }
    private val pixels = IntArray(FaceAlign.SIZE * FaceAlign.SIZE)
    private val aligned = Bitmap.createBitmap(FaceAlign.SIZE, FaceAlign.SIZE, Bitmap.Config.ARGB_8888)
    private val paint = Paint(Paint.FILTER_BITMAP_FLAG)

    fun find(sticker: Bitmap): List<FoundFace> {
        // Cut-out stickers are transparent around the person: detect on white, like a photo.
        val flat = Bitmap.createBitmap(sticker.width, sticker.height, Bitmap.Config.ARGB_8888)
        Canvas(flat).apply {
            drawColor(Color.WHITE)
            drawBitmap(sticker, 0f, 0f, null)
        }
        try {
            val faces = Tasks.await(detector.process(InputImage.fromBitmap(flat, 0)))
            val w = flat.width.toFloat()
            val h = flat.height.toFloat()
            return faces.mapNotNull { face ->
                if (face.boundingBox.width() < MIN_FACE_PIXELS) return@mapNotNull null
                // The template's image-left eye is the person's right eye.
                val points = listOf(
                    FaceLandmark.RIGHT_EYE, FaceLandmark.LEFT_EYE, FaceLandmark.NOSE_BASE,
                    FaceLandmark.MOUTH_RIGHT, FaceLandmark.MOUTH_LEFT,
                ).map { face.getLandmark(it)?.position ?: return@mapNotNull null }
                val box = face.boundingBox
                FoundFace(
                    (box.left / w).coerceIn(0f, 1f), (box.top / h).coerceIn(0f, 1f),
                    (box.right / w).coerceIn(0f, 1f), (box.bottom / h).coerceIn(0f, 1f),
                    embed(flat, points),
                )
            }
        } finally {
            flat.recycle()
        }
    }

    private fun embed(image: Bitmap, points: List<PointF>): FloatArray {
        val src = FloatArray(10).also { a -> points.forEachIndexed { i, p -> a[2 * i] = p.x; a[2 * i + 1] = p.y } }
        val matrix = Matrix().apply { setValues(FaceAlign.transform(src)) }
        Canvas(aligned).apply {
            drawColor(Color.WHITE)
            drawBitmap(image, matrix, paint)
        }
        aligned.getPixels(pixels, 0, FaceAlign.SIZE, 0, 0, FaceAlign.SIZE, FaceAlign.SIZE)
        // SFace takes RGB, 0-255, no normalization.
        input.rewind()
        val floats = input.asFloatBuffer()
        for (p in pixels) {
            floats.put(((p shr 16) and 0xFF).toFloat())
            floats.put(((p shr 8) and 0xFF).toFloat())
            floats.put((p and 0xFF).toFloat())
        }
        input.rewind()
        interpreter.run(input, output)
        val v = output[0]
        var norm = 0f
        for (x in v) norm += x * x
        val n = sqrt(norm).takeIf { it > 0f } ?: 1f
        return FloatArray(v.size) { v[it] / n }
    }

    override fun close() {
        detector.close()
        interpreter.close()
        aligned.recycle()
    }

    companion object {
        /** Keep in step with FetchFaceModel in core/vision/build.gradle.kts. */
        private const val ASSET = "faces/sface_fp16.tflite"

        /** Faces smaller than this share of the sticker's width are ignored. */
        private const val MIN_FACE_FRACTION = 0.1f

        /** Too few pixels to tell people apart. */
        private const val MIN_FACE_PIXELS = 40

        fun isBundled(context: Context): Boolean =
            context.assets.list("faces")?.contains(ASSET.substringAfter('/')) == true

        /** Maps the bundled SFace model straight from the APK (stored uncompressed). */
        @Throws(IOException::class)
        fun mapModel(context: Context): MappedByteBuffer =
            context.assets.openFd(ASSET).use { fd ->
                FileInputStream(fd.fileDescriptor).use { stream ->
                    stream.channel.map(FileChannel.MapMode.READ_ONLY, fd.startOffset, fd.declaredLength)
                }
            }
    }
}
