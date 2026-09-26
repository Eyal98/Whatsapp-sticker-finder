package com.eyal98.stickerfinder.vision

/**
 * Aligns a face to the 112x112 layout face-recognition models are trained on (ArcFace's five
 * points: both eyes, nose tip, mouth corners), with a similarity transform (rotation, uniform
 * scale, shift) fitted by least squares. Pure, for tests.
 */
object FaceAlign {

    const val SIZE = 112

    /** Image-left eye, image-right eye, nose, image-left mouth corner, image-right mouth corner. */
    val TEMPLATE = floatArrayOf(
        38.2946f, 51.6963f,
        73.5318f, 51.5014f,
        56.0252f, 71.7366f,
        41.5493f, 92.3655f,
        70.7299f, 92.2041f,
    )

    /**
     * The transform taking [src] points (x, y pairs, in [TEMPLATE] order) onto the template, as
     * an android.graphics.Matrix value array (row-major 3x3): [a, -b, tx, b, a, ty, 0, 0, 1].
     */
    fun transform(src: FloatArray, dst: FloatArray = TEMPLATE): FloatArray {
        require(src.size == dst.size && src.size % 2 == 0)
        val n = src.size / 2
        var sx = 0.0; var sy = 0.0; var dx = 0.0; var dy = 0.0
        for (i in 0 until n) {
            sx += src[2 * i]; sy += src[2 * i + 1]; dx += dst[2 * i]; dy += dst[2 * i + 1]
        }
        sx /= n; sy /= n; dx /= n; dy /= n
        var num1 = 0.0; var num2 = 0.0; var den = 0.0
        for (i in 0 until n) {
            val x = src[2 * i] - sx
            val y = src[2 * i + 1] - sy
            val u = dst[2 * i] - dx
            val v = dst[2 * i + 1] - dy
            num1 += x * u + y * v
            num2 += x * v - y * u
            den += x * x + y * y
        }
        require(den > 0) { "Degenerate face points" }
        val a = num1 / den
        val b = num2 / den
        val tx = dx - (a * sx - b * sy)
        val ty = dy - (b * sx + a * sy)
        return floatArrayOf(a.toFloat(), (-b).toFloat(), tx.toFloat(), b.toFloat(), a.toFloat(), ty.toFloat(), 0f, 0f, 1f)
    }
}
