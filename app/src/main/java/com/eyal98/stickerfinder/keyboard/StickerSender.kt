package com.eyal98.stickerfinder.keyboard

import android.content.ClipDescription
import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import androidx.core.content.FileProvider
import androidx.core.view.inputmethod.EditorInfoCompat
import androidx.core.view.inputmethod.InputConnectionCompat
import androidx.core.view.inputmethod.InputContentInfoCompat
import java.io.File
import java.io.IOException

/**
 * Hands a sticker to the app being typed in, the way keyboards insert stickers and GIFs
 * (commitContent). Apps that accept WhatsApp's sticker type get a real sticker instead of a photo.
 */
object StickerSender {

    /** WhatsApp's MIME type for stickers inserted from a keyboard. */
    const val WHATSAPP_STICKER = "image/webp.wasticker"
    private const val WEBP = "image/webp"
    private const val PNG = "image/png"

    sealed interface Result {
        data class Sent(val mimeType: String) : Result
        data object NotAccepted : Result
        data object Failed : Result
    }

    /** The best MIME type this field accepts for a sticker, or null if it takes no images. */
    fun chooseMimeType(editorInfo: EditorInfo): String? {
        val accepted = EditorInfoCompat.getContentMimeTypes(editorInfo).toList()
        fun accepts(type: String) = accepted.any { ClipDescription.compareMimeTypes(type, it) }
        return when {
            accepted.any { it.equals(WHATSAPP_STICKER, ignoreCase = true) } -> WHATSAPP_STICKER
            accepts(WEBP) -> WEBP
            accepts(PNG) -> PNG
            else -> null
        }
    }

    /** A sticker copied where the receiving app can read it. */
    class Prepared(val content: InputContentInfoCompat, val mimeType: String)

    /** Copies the sticker for sending. Does file I/O: call off the main thread. */
    fun prepare(context: Context, mimeType: String, stickerUri: Uri): Prepared? {
        val file = try {
            copyToShareable(context, stickerUri, asPng = mimeType == PNG)
        } catch (e: IOException) {
            return null
        } catch (e: SecurityException) {
            return null
        }
        val contentUri = FileProvider.getUriForFile(context, "${context.packageName}.stickers", file)
        return Prepared(InputContentInfoCompat(contentUri, ClipDescription("sticker", arrayOf(mimeType)), null), mimeType)
    }

    /** Inserts a prepared sticker. Call on the keyboard's main thread. */
    fun commit(editorInfo: EditorInfo, connection: InputConnection, prepared: Prepared): Result {
        val content = prepared.content
        val mimeType = prepared.mimeType
        val sent = InputConnectionCompat.commitContent(
            connection,
            editorInfo,
            content,
            // Lets the receiving app read this one file, and nothing else of ours.
            InputConnectionCompat.INPUT_CONTENT_GRANT_READ_URI_PERMISSION,
            null,
        )
        return if (sent) Result.Sent(mimeType) else Result.Failed
    }

    /**
     * Copies the sticker into our own cache, served by our FileProvider. Only the file being sent
     * is ever there: earlier ones are deleted first.
     */
    private fun copyToShareable(context: Context, source: Uri, asPng: Boolean): File {
        val dir = File(context.cacheDir, "send").apply {
            mkdirs()
            listFiles()?.forEach { it.delete() }
        }
        val target = File(dir, if (asPng) "sticker.png" else "sticker.webp")
        if (asPng) {
            val bitmap = ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, source))
            target.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            bitmap.recycle()
        } else {
            val input = context.contentResolver.openInputStream(source) ?: throw IOException("Cannot open sticker")
            input.use { inStream -> target.outputStream().use { inStream.copyTo(it) } }
        }
        return target
    }
}
