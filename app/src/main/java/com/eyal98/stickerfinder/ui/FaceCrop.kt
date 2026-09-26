package com.eyal98.stickerfinder.ui

import android.content.ContentResolver
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import com.eyal98.stickerfinder.data.FaceOnSticker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException

private const val DECODE_PX = 320

private object FaceCropCache {
    private val cache = LruCache<Long, ImageBitmap>(200)

    suspend fun load(resolver: ContentResolver, face: FaceOnSticker): ImageBitmap? {
        cache.get(face.id)?.let { return it }
        return withContext(Dispatchers.IO) {
            try {
                val sticker = ImageDecoder.decodeBitmap(ImageDecoder.createSource(resolver, Uri.parse(face.documentUri))) { d, _, _ ->
                    d.setTargetSize(DECODE_PX, DECODE_PX)
                    d.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                }
                // The face with some room around it, as a square.
                val w = sticker.width
                val h = sticker.height
                val cx = (face.x0 + face.x1) / 2 * w
                val cy = (face.y0 + face.y1) / 2 * h
                val half = maxOf((face.x1 - face.x0) * w, (face.y1 - face.y0) * h) * 0.7f
                val left = (cx - half).toInt().coerceIn(0, w - 1)
                val top = (cy - half).toInt().coerceIn(0, h - 1)
                val size = minOf((2 * half).toInt().coerceAtLeast(1), w - left, h - top)
                Bitmap.createBitmap(sticker, left, top, size, size).asImageBitmap().also { cache.put(face.id, it) }
            } catch (e: IOException) {
                null
            } catch (e: SecurityException) {
                null
            } catch (e: IllegalArgumentException) {
                null
            }
        }
    }
}

/** A crop of one face from its sticker. */
@Composable
fun FaceCrop(face: FaceOnSticker, modifier: Modifier = Modifier) {
    val resolver = LocalContext.current.contentResolver
    var bitmap by remember(face.id) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(face.id) { bitmap = FaceCropCache.load(resolver, face) }
    Box(modifier) {
        bitmap?.let { Image(it, contentDescription = null, modifier = Modifier.matchParentSize()) }
    }
}
