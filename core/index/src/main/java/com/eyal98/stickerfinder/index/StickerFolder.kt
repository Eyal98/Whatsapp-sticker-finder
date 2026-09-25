package com.eyal98.stickerfinder.index

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import androidx.core.content.edit

/**
 * The one folder the app may read: WhatsApp's sticker folder, granted by the user through the
 * system folder picker. The grant is read-only and persisted across reboots.
 */
object StickerFolder {

    private const val PREFS = "sticker_folder"
    private const val KEY_TREE_URI = "tree_uri"

    private const val EXTERNAL_STORAGE_AUTHORITY = "com.android.externalstorage.documents"

    /** Where WhatsApp keeps sticker files on Android 11+. Used to open the picker in the right place. */
    private const val WHATSAPP_STICKERS_DOC_ID =
        "primary:Android/media/com.whatsapp/WhatsApp/Media/WhatsApp Stickers"

    val pickerStartUri: Uri
        get() = DocumentsContract.buildDocumentUri(EXTERNAL_STORAGE_AUTHORITY, WHATSAPP_STICKERS_DOC_ID)

    /** Keeps read access to [treeUri] and forgets any previously granted folder. */
    fun persist(context: Context, treeUri: Uri) {
        val resolver = context.contentResolver
        resolver.takePersistableUriPermission(treeUri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        resolver.persistedUriPermissions
            .filter { it.uri != treeUri }
            .forEach {
                resolver.releasePersistableUriPermission(it.uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        prefs(context).edit { putString(KEY_TREE_URI, treeUri.toString()) }
    }

    /** The granted folder, or null if none was chosen or the user revoked access. */
    fun current(context: Context): Uri? {
        val uri = prefs(context).getString(KEY_TREE_URI, null)?.let(Uri::parse) ?: return null
        val granted = context.contentResolver.persistedUriPermissions
            .any { it.uri == uri && it.isReadPermission }
        return uri.takeIf { granted }
    }

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
