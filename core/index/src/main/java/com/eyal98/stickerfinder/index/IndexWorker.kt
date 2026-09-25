package com.eyal98.stickerfinder.index

import android.content.Context
import android.util.Log
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.eyal98.stickerfinder.data.StickerDatabase
import com.eyal98.stickerfinder.data.StickerRepository
import com.eyal98.stickerfinder.embed.EmbedderHolder
import com.eyal98.stickerfinder.ocr.TesseractTextReader
import java.util.concurrent.TimeUnit

/** Implemented by the Application so the worker can reach the shared database. */
interface StickerIndexHost {
    val database: StickerDatabase
    val repository: StickerRepository
    val embedders: EmbedderHolder
}

/** Scans the sticker folder and indexes new or changed stickers. */
class IndexWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val host = applicationContext as StickerIndexHost
        val treeUri = StickerFolder.current(applicationContext) ?: return Result.failure()
        val dao = host.database.stickerDao()
        val resolver = applicationContext.contentResolver
        // Null if the language files failed to load; stickers then get basic indexing and are
        // picked up for OCR on a later run.
        val textReader = TesseractTextReader.create(applicationContext)
        if (textReader == null) Log.w(TAG, "OCR unavailable")
        return try {
            StickerScanner(resolver, dao).scan(treeUri)
            // OCR takes a fraction of a second per sticker, so a first run over a large folder
            // may hit WorkManager's time limit; the work is then stopped and resumes later from
            // where it left off, since each sticker is saved as soon as it's done.
            val indexed = StickerIndexer(resolver, dao, host.repository, textReader).indexPending()
            // New printed text changes what stickers mean for semantic search.
            if (indexed > 0) EmbedWorker.runNow(applicationContext)
            Result.success()
        } catch (e: SecurityException) {
            Log.w(TAG, "Folder access was revoked", e)
            Result.failure()
        } catch (e: IllegalStateException) {
            Log.w(TAG, "Folder could not be listed", e)
            Result.retry()
        } finally {
            textReader?.close()
        }
    }

    companion object {
        private const val TAG = "IndexWorker"
        private const val NOW = "sticker-index-now"
        private const val PERIODIC = "sticker-index-periodic"

        /** Runs a scan right away, e.g. after the folder is granted or when the app opens. */
        fun runNow(context: Context) {
            WorkManager.getInstance(context).enqueueUniqueWork(
                NOW,
                ExistingWorkPolicy.KEEP,
                OneTimeWorkRequestBuilder<IndexWorker>().build(),
            )
        }

        /** Picks up newly saved stickers in the background. */
        fun schedulePeriodic(context: Context) {
            val constraints = Constraints.Builder().setRequiresBatteryNotLow(true).build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                PERIODIC,
                ExistingPeriodicWorkPolicy.KEEP,
                PeriodicWorkRequestBuilder<IndexWorker>(6, TimeUnit.HOURS)
                    .setConstraints(constraints)
                    .build(),
            )
        }
    }
}
