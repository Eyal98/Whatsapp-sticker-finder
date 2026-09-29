package com.eyal98.stickerfinder.keyboard

import android.database.Cursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import androidx.core.content.FileProvider

/**
 * Serves stickers sent from the keyboard (see [StickerSender]), like a plain FileProvider, and
 * notes in [SendLog] how the receiving app reads them: that's what tells why a send failed.
 */
class SentStickerProvider : FileProvider() {

    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor? =
        record(uri, "open", mode) { super.openFile(uri, mode) }

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?,
    ): Cursor = record(uri, "query", null) { super.query(uri, projection, selection, selectionArgs, sortOrder) }

    override fun getType(uri: Uri): String? = record(uri, "type", null) { super.getType(uri) }

    private inline fun <T> record(uri: Uri, kind: String, mode: String?, block: () -> T): T {
        val context = context
        // Each sticker's copy is in its own folder (see StickerSender): the folder names it.
        val file = uri.pathSegments.let { if (it.size >= 3) it[it.size - 2] else it.lastOrNull() }.orEmpty()
        return try {
            block().also { if (context != null) log { SendLog.accessed(context, file, kind, mode, null) } }
        } catch (e: Exception) {
            if (context != null) log { SendLog.accessed(context, file, kind, mode, e) }
            throw e
        }
    }

    /** Logging must never break serving the file. */
    private inline fun log(block: () -> Unit) {
        try {
            block()
        } catch (e: Exception) {
            // Ignored.
        }
    }
}
