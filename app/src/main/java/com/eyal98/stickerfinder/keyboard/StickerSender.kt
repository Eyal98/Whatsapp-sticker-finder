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
import java.util.UUID

/**
 * Hands a sticker to the app being typed in, the way keyboards insert stickers and GIFs
 * (commitContent). Apps that accept WhatsApp's sticker type get a real sticker instead of a photo.
 */
object StickerSender {

    /** WhatsApp's MIME type for stickers inserted from a keyboard. */
    const val WHATSAPP_STICKER = "image/webp.wasticker"
    private const val WEBP = "image/webp"
    private const val PNG = "image/png"

    /** How long a sent copy stays readable: enough for the receiver to read it once. */
    private const val KEEP_SENT_MILLIS = 10 * 60 * 1000L

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
     * Copies the sticker into our own cache, served by our FileProvider, under a new random name
     * for every send. A receiving app keeps its read grant for as long as it holds the content,
     * so a reused name would let an app that got an earlier sticker read a later one meant for
     * someone else. Earlier copies are removed once they're old enough that no receiver is still
     * reading them.
     */
    private fun copyToShareable(context: Context, source: Uri, asPng: Boolean): File {
        val dir = File(context.cacheDir, "send").apply { mkdirs() }
        val now = System.currentTimeMillis()
        dir.listFiles()?.filter { now - it.lastModified() > KEEP_SENT_MILLIS }?.forEach { it.delete() }
        val target = File(dir, "${UUID.randomUUID()}.${if (asPng) "png" else "webp"}")
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
