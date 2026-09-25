package com.eyal98.stickerfinder.ui

import android.content.ContentResolver
import android.graphics.ImageDecoder
import android.net.Uri
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException

private const val THUMBNAIL_PX = 192

private object ThumbnailCache {
    private val cache = LruCache<String, ImageBitmap>(300)

    suspend fun load(resolver: ContentResolver, uri: String): ImageBitmap? {
        cache.get(uri)?.let { return it }
        return withContext(Dispatchers.IO) {
            try {
                ImageDecoder.decodeBitmap(ImageDecoder.createSource(resolver, Uri.parse(uri))) { decoder, _, _ ->
                    decoder.setTargetSize(THUMBNAIL_PX, THUMBNAIL_PX)
                }.asImageBitmap().also { cache.put(uri, it) }
            } catch (e: IOException) {
                null
            } catch (e: SecurityException) {
                null
            }
        }
    }
}

/** Shows the first frame of a sticker. Animated playback can come later. */
@Composable
fun StickerThumbnail(documentUri: String, contentDescription: String?, modifier: Modifier = Modifier) {
    val resolver = LocalContext.current.contentResolver
    val bitmap by produceState<ImageBitmap?>(initialValue = null, documentUri) {
        value = ThumbnailCache.load(resolver, documentUri)
    }
    val image = bitmap
    if (image != null) {
        Image(bitmap = image, contentDescription = contentDescription, modifier = modifier)
    } else {
        Box(modifier)
    }
}
