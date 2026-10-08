package com.eyal98.stickerfinder

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint

/** Black text on white, like a sticker with a caption. */
internal fun textBitmap(text: String): Bitmap {
    val bitmap = Bitmap.createBitmap(512, 512, Bitmap.Config.ARGB_8888)
    Canvas(bitmap).apply {
        drawColor(Color.WHITE)
        drawText(text, 40f, 280f, Paint().apply { color = Color.BLACK; textSize = 110f; isAntiAlias = true })
    }
    return bitmap
}

/** A red disc on white: something for the picture model to look at. */
internal fun shapeBitmap(): Bitmap {
    val bitmap = Bitmap.createBitmap(512, 512, Bitmap.Config.ARGB_8888)
    Canvas(bitmap).apply {
        drawColor(Color.WHITE)
        drawCircle(256f, 256f, 180f, Paint().apply { color = Color.RED; isAntiAlias = true })
    }
    return bitmap
}
