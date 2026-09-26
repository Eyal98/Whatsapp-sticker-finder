package com.eyal98.stickerfinder.vision

import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.math.cos
import kotlin.math.sin

class FaceAlignTest {

    private fun apply(m: FloatArray, x: Float, y: Float) =
        Pair(m[0] * x + m[1] * y + m[2], m[3] * x + m[4] * y + m[5])

    @Test
    fun `recovers a rotated, scaled and shifted face exactly`() {
        // The template moved: scaled by 3, rotated 20 degrees, shifted.
        val angle = Math.toRadians(20.0)
        val src = FloatArray(FaceAlign.TEMPLATE.size)
        for (i in 0 until 5) {
            val x = FaceAlign.TEMPLATE[2 * i]
            val y = FaceAlign.TEMPLATE[2 * i + 1]
            src[2 * i] = (3 * (x * cos(angle) - y * sin(angle)) + 40).toFloat()
            src[2 * i + 1] = (3 * (x * sin(angle) + y * cos(angle)) - 15).toFloat()
        }
        val m = FaceAlign.transform(src)
        for (i in 0 until 5) {
            val (u, v) = apply(m, src[2 * i], src[2 * i + 1])
            assertEquals(FaceAlign.TEMPLATE[2 * i], u, 0.01f)
            assertEquals(FaceAlign.TEMPLATE[2 * i + 1], v, 0.01f)
        }
    }
}
