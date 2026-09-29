package com.eyal98.stickerfinder.keyboard

import android.content.ClipDescription
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import androidx.core.content.FileProvider
import androidx.core.view.inputmethod.EditorInfoCompat
import androidx.core.view.inputmethod.InputConnectionCompat
import androidx.core.view.inputmethod.InputContentInfoCompat
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.security.MessageDigest

/**
 * Hands a sticker to the app being typed in, the way keyboards insert stickers and GIFs
 * (commitContent). Apps that accept WhatsApp's sticker type get a real sticker instead of a photo.
 */
object StickerSender {

    /** WhatsApp's MIME type for stickers inserted from a keyboard. */
    const val WHATSAPP_STICKER = "image/webp.wasticker"
    private const val WEBP = "image/webp"
    private const val PNG = "image/png"

    /**
     * How long a sent copy is kept. WhatsApp doesn't always copy a sticker at once: for animated
     * ones it keeps the link and reads the file again later (its preview, its own copy), and a
     * copy deleted after ten minutes showed as "doesn't exist on your internal storage".
     */
    private const val KEEP_SENT_MILLIS = 30L * 24 * 60 * 60 * 1000

    /** Sent copies are also trimmed, oldest first, to stay under this. */
    private const val MAX_SENT_BYTES = 100L * 1024 * 1024

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

    /** Whether the copy had to be made to fit WhatsApp's rules, and how it went. */
    enum class Shrink { NOT_NEEDED, SHRUNK, CACHED, FAILED }

    /**
     * The copy to share, how it was made, the sticker's own size, and what the sticker and the
     * copy hold (for the problem report; numbers only).
     */
    private class Copy(
        val file: File,
        val shrink: Shrink,
        val originalBytes: Long,
        val original: StickerShrinker.Info? = null,
        val sent: StickerShrinker.Info? = null,
    )

    /** A sticker copied where the receiving app can read it. */
    class Prepared(val content: InputContentInfoCompat, val mimeType: String, val fileName: String)

    /**
     * Copies the sticker for sending to [targetPackage] (the app being typed in). Does file I/O:
     * call off the main thread.
     */
    fun prepare(
        context: Context,
        mimeType: String,
        stickerUri: Uri,
        targetPackage: String?,
        animated: Boolean,
        onFitting: () -> Unit = {},
    ): Prepared? {
        val copy = try {
            copyToShareable(context, stickerUri, asPng = mimeType == PNG, animated = animated, onFitting = onFitting)
        } catch (e: IOException) {
            return null
        } catch (e: SecurityException) {
            return null
        }
        val file = copy.file
        val contentUri = FileProvider.getUriForFile(context, "${context.packageName}.stickers", file)
        // The keyboard's grant (commit below) can end when the keyboard closes, while WhatsApp may
        // come back to an animated sticker later: give that app its own lasting read access to
        // this one file.
        if (!targetPackage.isNullOrEmpty()) {
            try {
                context.grantUriPermission(targetPackage, contentUri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            } catch (e: SecurityException) {
                // The keyboard's own grant still applies.
            }
        }
        val targetUid = try {
            targetPackage?.let { context.packageManager.getPackageUid(it, 0) } ?: -1
        } catch (e: PackageManager.NameNotFoundException) {
            -1
        }
        SendLog.started(
            context, file.name, animated, mimeType, file.length(), targetUid, copy.shrink, copy.originalBytes,
            copy.original?.describe(), copy.sent?.describe(),
        )
        return Prepared(InputContentInfoCompat(contentUri, ClipDescription("sticker", arrayOf(mimeType)), null), mimeType, file.name)
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
     * Copies the sticker into app storage, served by our FileProvider, named after its content
     * (SHA-256). A name can then only ever hold that one sticker: an app that was sent it before
     * and still has access can't read a different sticker sent later to someone else. The same
     * sticker sent again reuses its file. Copies are kept [KEEP_SENT_MILLIS] (WhatsApp may read
     * them again) and trimmed to [MAX_SENT_BYTES].
     */
    private fun copyToShareable(context: Context, source: Uri, asPng: Boolean, animated: Boolean, onFitting: () -> Unit): Copy {
        val dir = File(context.filesDir, "sent").apply { mkdirs() }
        trim(dir)
        val bytes = if (asPng) {
            val bitmap = ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, source))
            try {
                ByteArrayOutputStream().also { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }.toByteArray()
            } finally {
                bitmap.recycle()
            }
        } else {
            val input = context.contentResolver.openInputStream(source) ?: throw IOException("Cannot open sticker")
            input.use { it.readBytes() }
        }
        val name = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }.take(40)

        // Breaks WhatsApp's rules for animated stickers (too big, not 512 x 512, too long, frames
        // too short): send a copy that fits, made once and kept like the others (so sending it
        // again is instant).
        val info = if (!asPng && animated) StickerShrinker.info(bytes) else null
        if (info != null && info.animated && info.needsFitting) {
            val size = bytes.size.toLong()
            val fitted = File(dir, "$name-fit.webp")
            if (fitted.isFile && fitted.length() in 1..StickerShrinker.MAX_ANIMATED_BYTES) {
                fitted.setLastModified(System.currentTimeMillis())
                return Copy(fitted, Shrink.CACHED, size, info, StickerShrinker.info(fitted.readBytes()))
            }
            onFitting()
            val shrunk = StickerShrinker.shrink(bytes)
            if (shrunk != null) {
                save(dir, fitted, shrunk)
                return Copy(fitted, Shrink.SHRUNK, size, info, StickerShrinker.info(shrunk))
            }
            // Sent as it is: it may still go through.
            return Copy(save(dir, File(dir, "$name.webp"), bytes), Shrink.FAILED, size, info)
        }
        return Copy(save(dir, File(dir, "$name.${if (asPng) "png" else "webp"}"), bytes), Shrink.NOT_NEEDED, bytes.size.toLong(), info)
    }

    /** Writes [bytes] to [target] (via a temporary file), unless it already holds them. */
    private fun save(dir: File, target: File, bytes: ByteArray): File {
        if (target.isFile && target.length() == bytes.size.toLong()) {
            target.setLastModified(System.currentTimeMillis())
        } else {
            val tmp = File(dir, "${target.name}.tmp")
            tmp.writeBytes(bytes)
            if (!tmp.renameTo(target)) throw IOException("Could not save the sticker to send")
        }
        return target
    }

    /** Removes copies older than [KEEP_SENT_MILLIS], then the oldest until under [MAX_SENT_BYTES]. */
    private fun trim(dir: File) {
        val now = System.currentTimeMillis()
        val files = dir.listFiles().orEmpty().sortedBy { it.lastModified() }.toMutableList()
        files.filter { now - it.lastModified() > KEEP_SENT_MILLIS }.forEach { it.delete(); files.remove(it) }
        var total = files.sumOf { it.length() }
        for (f in files) {
            if (total <= MAX_SENT_BYTES) break
            total -= f.length()
            f.delete()
        }
    }
}
