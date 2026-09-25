package com.eyal98.stickerfinder.index

import android.content.ContentResolver
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.DocumentsContract.Document
import com.eyal98.stickerfinder.data.StickerDao
import com.eyal98.stickerfinder.data.StickerEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class ScanResult(val found: Int, val added: Int, val changed: Int, val removed: Int)

/** Brings the database in line with the files currently in the granted folder. */
class StickerScanner(
    private val resolver: ContentResolver,
    private val dao: StickerDao,
) {

    private data class StickerFile(val uri: String, val name: String, val size: Long, val lastModified: Long)

    suspend fun scan(treeUri: Uri): ScanResult = withContext(Dispatchers.IO) {
        val files = listStickerFiles(treeUri)
        val existing = dao.fileStates().associateBy { it.documentUri }

        val added = mutableListOf<StickerEntity>()
        var changed = 0
        for (file in files) {
            val known = existing[file.uri]
            when {
                known == null -> added += StickerEntity(
                    documentUri = file.uri,
                    displayName = file.name,
                    sizeBytes = file.size,
                    lastModified = file.lastModified,
                )
                known.sizeBytes != file.size || known.lastModified != file.lastModified -> {
                    dao.markChanged(known.id, file.size, file.lastModified)
                    changed++
                }
            }
        }
        added.chunked(500).forEach { dao.insertAll(it) }

        // An empty listing is more likely a storage hiccup than every sticker being deleted, and
        // deleting would lose the user's stars and tags, so only remove when some files remain.
        val seen = files.mapTo(HashSet()) { it.uri }
        val removed = if (files.isEmpty()) emptyList() else existing.values.filter { it.documentUri !in seen }.map { it.id }
        if (removed.isNotEmpty()) dao.deleteWithFts(removed)

        ScanResult(found = files.size, added = added.size, changed = changed, removed = removed.size)
    }

    private fun listStickerFiles(treeUri: Uri): List<StickerFile> {
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(
            treeUri,
            DocumentsContract.getTreeDocumentId(treeUri),
        )
        val projection = arrayOf(
            Document.COLUMN_DOCUMENT_ID,
            Document.COLUMN_DISPLAY_NAME,
            Document.COLUMN_MIME_TYPE,
            Document.COLUMN_SIZE,
            Document.COLUMN_LAST_MODIFIED,
        )
        val cursor = resolver.query(childrenUri, projection, null, null, null)
            ?: error("Could not list $treeUri")
        return cursor.use { c ->
            buildList {
                while (c.moveToNext()) {
                    val name = c.getString(1) ?: continue
                    val mime = c.getString(2)
                    val isWebp = mime == "image/webp" || name.endsWith(".webp", ignoreCase = true)
                    if (mime == Document.MIME_TYPE_DIR || !isWebp) continue
                    val docUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, c.getString(0))
                    add(StickerFile(docUri.toString(), name, c.getLong(3), c.getLong(4)))
                }
            }
        }
    }
}
