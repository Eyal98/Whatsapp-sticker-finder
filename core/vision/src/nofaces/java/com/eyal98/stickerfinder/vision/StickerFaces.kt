package com.eyal98.stickerfinder.vision

import android.content.Context
import android.graphics.Bitmap
import java.io.Closeable
import java.nio.ByteBuffer
import java.nio.MappedByteBuffer

class FoundFace(val x0: Float, val y0: Float, val x1: Float, val y1: Float, val vector: FloatArray)

/** Built without the face model (core/vision/faces.properties is UNPINNED): never bundled, so never run. */
class StickerFaces(@Suppress("UNUSED_PARAMETER") model: ByteBuffer) : Closeable {

    fun find(sticker: Bitmap): List<FoundFace> = error(NOT_BUNDLED)

    override fun close() {}

    companion object {
        private const val NOT_BUNDLED = "People is not in this build"

        fun isBundled(context: Context): Boolean = false

        fun mapModel(context: Context): MappedByteBuffer = error(NOT_BUNDLED)
    }
}
