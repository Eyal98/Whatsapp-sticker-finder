package com.eyal98.stickerfinder.index

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.core.content.edit
import com.eyal98.stickerfinder.data.Person
import com.eyal98.stickerfinder.data.StickerDao
import com.eyal98.stickerfinder.data.StickerFace
import com.eyal98.stickerfinder.search.FaceGrouping
import com.eyal98.stickerfinder.search.Vectors
import com.eyal98.stickerfinder.vision.StickerFaces
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.IOException

/**
 * The People feature is opt-in: it keeps face vectors (biometric data, on the phone only), so
 * nothing is scanned until the user turns it on from the People screen.
 */
object FaceSettings {
    private const val PREFS = "faces"
    private const val ENABLED = "enabled"

    fun isEnabled(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(ENABLED, false)

    fun setEnabled(context: Context, enabled: Boolean) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit { putBoolean(ENABLED, enabled) }
}

/** Looks for faces on stickers that haven't been scanned, and stores them. */
class FaceScanner(
    private val resolver: ContentResolver,
    private val dao: StickerDao,
    private val faces: StickerFaces,
) {

    suspend fun scanPending(
        budget: WorkBudget,
        onScanned: suspend (processed: Int) -> Unit = {},
    ): StickerIndexer.Progress = withContext(Dispatchers.Default) {
        var processed = 0
        var batch = dao.needingFaceScan(BATCH)
        while (batch.isNotEmpty()) {
            for (sticker in batch) {
                ensureActive()
                if (budget.exhausted) return@withContext StickerIndexer.Progress(processed, finished = false)
                // Counted first, so a sticker that crashes the model is skipped after MAX_ATTEMPTS.
                dao.markFaceScanAttempt(sticker.id)
                val found = if (sticker.faceScanAttempts >= WorkBudget.MAX_ATTEMPTS) {
                    emptyList()
                } else {
                    find(sticker.documentUri)
                }
                val rows = found.map {
                    StickerFace(
                        stickerId = sticker.id, x0 = it.x0, y0 = it.y0, x1 = it.x1, y1 = it.y1,
                        vector = Vectors.encode(it.vector),
                    )
                }
                withContext(NonCancellable) { dao.saveFaces(sticker.id, rows) }
                processed++
                onScanned(processed)
            }
            batch = dao.needingFaceScan(BATCH)
        }
        StickerIndexer.Progress(processed, finished = true)
    }

    private fun find(documentUri: String) = try {
        val bitmap = StickerBitmaps.decode(resolver, Uri.parse(documentUri))
        try {
            faces.find(bitmap)
        } finally {
            bitmap.recycle()
        }
    } catch (e: IOException) {
        Log.w(TAG, "Could not read sticker", e)
        emptyList()
    } catch (e: SecurityException) {
        Log.w(TAG, "Lost access to sticker", e)
        emptyList()
    } catch (e: RuntimeException) {
        // ML Kit reports detection failures as ExecutionException wrapped in RuntimeException.
        Log.w(TAG, "Face scan failed", e)
        emptyList()
    } catch (e: java.util.concurrent.ExecutionException) {
        Log.w(TAG, "Face detection failed", e)
        emptyList()
    }

    private companion object {
        const val TAG = "FaceScanner"
        const val BATCH = 20
    }
}

/** Puts ungrouped faces into people groups, and keeps stickers' searchable names in step. */
object FaceGrouper {

    /**
     * Bumped when grouping changes enough that existing groups should be rebuilt: version 1
     * compared faces with group averages and put almost everyone in one group.
     */
    private const val VERSION = 2
    private const val PREFS = "face_grouping"

    /** Returns how many stickers' searchable names changed. */
    suspend fun regroup(context: Context, dao: StickerDao): Int = withContext(Dispatchers.Default) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (prefs.getInt("version", 1) < VERSION) {
            withContext(NonCancellable) { dao.resetGroups() }
            prefs.edit { putInt("version", VERSION) }
        }
        val rows = dao.faceRows()
        val grouped = rows.filter { it.personId != null }
            .map { FaceGrouping.Face(it.id, Vectors.decode(it.vector)) to it.personId!! }
        val ungrouped = rows.filter { it.personId == null && !it.locked }
            .map { FaceGrouping.Face(it.id, Vectors.decode(it.vector)) }
        val result = FaceGrouping.group(grouped, ungrouped)
        withContext(NonCancellable) {
            result.joined.entries.groupBy({ it.value }, { it.key }).forEach { (person, faces) -> dao.assignFaces(faces, person) }
            for (faces in result.newGroups) dao.assignFaces(faces, dao.insertPerson(Person()))
            dao.syncPeopleNames()
        }
    }
}
