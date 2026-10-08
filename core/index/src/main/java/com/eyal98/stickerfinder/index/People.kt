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
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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

/**
 * Serializes every write of face data with deleting it. Turning People off cancels the face job,
 * but a detection already running would otherwise save its faces after the delete. Writers take
 * this lock and check People is still on; [deleteAll] takes it too, so once it returns no face
 * data comes back. Lock order: Backup's lock first, then this one; never Backup's inside this.
 */
object FaceData {
    private val lock = Mutex()

    /** Runs [write] only if People is still on, never at the same time as [deleteAll]. */
    suspend fun <T> writeIfEnabled(context: Context, write: suspend () -> T): T? =
        withContext(NonCancellable) {
            lock.withLock { if (FaceSettings.isEnabled(context)) write() else null }
        }

    /** Turns People off and deletes every face vector, group and name. */
    suspend fun deleteAll(context: Context, dao: StickerDao) = withContext(NonCancellable) {
        lock.withLock {
            FaceSettings.setEnabled(context, false)
            dao.deleteFaceData()
        }
    }
}

/** Looks for faces on stickers that haven't been scanned, and stores them. */
class FaceScanner(
    private val context: Context,
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
                // People may have been turned off (and its data deleted) during the detection.
                FaceData.writeIfEnabled(context) { dao.saveFaces(sticker.id, rows) }
                    ?: return@withContext StickerIndexer.Progress(processed, finished = true)
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

    /**
     * Bumped when face vectors change meaning: version 1 aligned faces upside down (so every
     * vector was alike); stickers are scanned for faces again.
     */
    private const val VECTOR_VERSION = 2

    /** Rescans every sticker for faces if the stored vectors are from an older version. */
    suspend fun migrateVectors(context: Context, dao: StickerDao) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (prefs.getInt("vector_version", 1) >= VECTOR_VERSION) return
        withContext(NonCancellable) { dao.deleteFaceData() }
        prefs.edit { putInt("vector_version", VECTOR_VERSION) }
    }

    /** Returns how many stickers' searchable names changed. */
    suspend fun regroup(context: Context, dao: StickerDao): Int = withContext(Dispatchers.Default) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (prefs.getInt("version", 1) < VERSION) {
            FaceData.writeIfEnabled(context) { dao.resetGroups() }
            prefs.edit { putInt("version", VERSION) }
        }
        val rows = dao.faceRows()
        val grouped = rows.filter { it.personId != null }
            .map { FaceGrouping.Face(it.id, Vectors.decode(it.vector)) to it.personId!! }
        val ungrouped = rows.filter { it.personId == null && !it.locked }
            .map { FaceGrouping.Face(it.id, Vectors.decode(it.vector)) }
        val typical = FaceGrouping.pairStats(rows.map { Vectors.decode(it.vector) })?.first
        if (typical != null && typical > FaceGrouping.MAX_TYPICAL_SIMILARITY) {
            Log.w("FaceGrouper", "Face vectors are too alike (median $typical); not grouping")
            return@withContext 0
        }
        val result = FaceGrouping.group(grouped, ungrouped)
        FaceData.writeIfEnabled(context) {
            result.joined.entries.groupBy({ it.value }, { it.key }).forEach { (person, faces) -> dao.assignFaces(faces, person) }
            for (faces in result.newGroups) dao.assignFaces(faces, dao.insertPerson(Person()))
            dao.syncPeopleNames()
        } ?: 0
    }
}
