package com.eyal98.stickerfinder.index

import android.content.Context
import android.util.Log
import androidx.core.content.edit
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.eyal98.stickerfinder.index.WorkBudget.Companion.continueSoon
import com.eyal98.stickerfinder.data.IndexVersion
import com.eyal98.stickerfinder.data.StickerDao
import com.eyal98.stickerfinder.data.StickerDatabase
import com.eyal98.stickerfinder.data.StickerRepository
import com.eyal98.stickerfinder.embed.EmbedderHolder
import com.eyal98.stickerfinder.ml.DeviceCapability
import com.eyal98.stickerfinder.ocr.TesseractTextReader
import kotlinx.coroutines.flow.first
import java.util.concurrent.TimeUnit
import kotlin.coroutines.cancellation.CancellationException

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
        // Empty if the language files failed to load; stickers then get basic indexing and are
        // picked up for OCR on a later run.
        val textReaders = createTextReaders()
        if (textReaders.isEmpty()) Log.w(TAG, "OCR unavailable")
        return try {
            // Listing a folder of 10,000+ files through the storage provider is expensive, so
            // continuation runs (retries of the same request) skip it.
            if (runAttemptCount == 0 && scanDue(treeUri.toString())) {
                StickerScanner(resolver, dao).scan(treeUri)
                scanDone(treeUri.toString())
            }

            // Started from the app, indexing runs as a foreground job: Android then doesn't stop
            // it after 10 minutes, throttle it, or kill the app while it works in the background.
            // Started in the background, Android doesn't allow that, so it runs in short slices.
            val foreground = tryForeground(pendingCount(dao))
            val budget = WorkBudget(if (foreground) FOREGROUND_BUDGET_MILLIS else WorkBudget.DEFAULT_MILLIS)
            val start = System.currentTimeMillis()
            val progress = StickerIndexer(resolver, dao, textReaders).indexPending(budget) {
                if (foreground) tryForeground(pendingCount(dao))
            }
            IndexStats.record(applicationContext, progress.processed, System.currentTimeMillis() - start, textReaders.size, foreground)
            // New printed text changes what stickers mean for semantic search.
            if (progress.processed > 0) {
                EmbedWorker.runNow(applicationContext)
                ImageTagWorker.runNow(applicationContext)
            }
            if (progress.finished) Result.success() else Result.retry()
        } catch (e: CancellationException) {
            throw e
        } catch (e: SecurityException) {
            Log.w(TAG, "Folder access was revoked", e)
            Result.failure()
        } catch (e: IllegalStateException) {
            Log.w(TAG, "Folder could not be listed", e)
            Result.retry()
        } finally {
            textReaders.forEach { it.close() }
        }
    }

    /**
     * One OCR reader per parallel worker: half the CPU cores (leaving the rest for the phone),
     * at most [MAX_READERS], and fewer on phones with little memory.
     */
    private fun createTextReaders(): List<TesseractTextReader> {
        val byCores = (Runtime.getRuntime().availableProcessors() / 2).coerceIn(1, MAX_READERS)
        val lowMemory = DeviceCapability.totalRamBytes(applicationContext) < LOW_MEMORY_BYTES
        val count = if (lowMemory) byCores.coerceAtMost(2) else byCores
        val first = TesseractTextReader.create(applicationContext) ?: return emptyList()
        return listOf(first) + List(count - 1) { TesseractTextReader.create(applicationContext) }.filterNotNull()
    }

    private suspend fun pendingCount(dao: StickerDao): Int =
        dao.observePendingCount(IndexVersion.CURRENT).first()

    /** Shows the progress notification and keeps the job in the foreground, if Android allows it. */
    private suspend fun tryForeground(left: Int): Boolean =
        try {
            setForeground(IndexNotification.foregroundInfo(applicationContext, left))
            true
        } catch (e: IllegalStateException) {
            // ForegroundServiceStartNotAllowedException: the app isn't in the foreground.
            false
        } catch (e: SecurityException) {
            false
        }

    private fun prefs() = applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** A newly chosen folder is always listed. */
    private fun scanDue(folder: String): Boolean =
        prefs().getString(LAST_SCAN_FOLDER, null) != folder ||
            System.currentTimeMillis() - prefs().getLong(LAST_SCAN, 0) !in 0 until SCAN_COOLDOWN_MILLIS

    private fun scanDone(folder: String) = prefs().edit {
        putLong(LAST_SCAN, System.currentTimeMillis())
        putString(LAST_SCAN_FOLDER, folder)
    }

    companion object {
        private const val TAG = "IndexWorker"
        private const val NOW = "sticker-index-now"
        private const val PERIODIC = "sticker-index-periodic"

        /** Unique work names, for diagnostics. */
        val UNIQUE_NAMES = listOf(NOW, PERIODIC)

        private const val MAX_READERS = 4
        private const val LOW_MEMORY_BYTES = 4_000_000_000L

        /** Well under Android's 6-hour daily limit for this kind of foreground job. */
        private const val FOREGROUND_BUDGET_MILLIS = 2 * 60 * 60 * 1000L

        /**
         * Starts indexing now, while the app is open. A run waiting out its retry delay is
         * replaced so it starts immediately (and in the foreground); a running one is kept.
         */
        suspend fun startNow(context: Context) {
            val workManager = WorkManager.getInstance(context)
            val running = workManager.getWorkInfosForUniqueWorkFlow(NOW).first()
                .any { it.state == WorkInfo.State.RUNNING }
            workManager.enqueueUniqueWork(
                NOW,
                if (running) ExistingWorkPolicy.KEEP else ExistingWorkPolicy.REPLACE,
                OneTimeWorkRequestBuilder<IndexWorker>().continueSoon().build(),
            )
        }

        /** Runs a scan right away unless one is already queued or running. */
        fun runNow(context: Context) {
            WorkManager.getInstance(context).enqueueUniqueWork(
                NOW,
                ExistingWorkPolicy.KEEP,
                OneTimeWorkRequestBuilder<IndexWorker>().continueSoon().build(),
            )
        }

        private const val PREFS = "index_worker"
        private const val LAST_SCAN = "last_scan"
        private const val LAST_SCAN_FOLDER = "last_scan_folder"

        /**
         * Reopening the app within this long doesn't list the folder again: WhatsApp adds a few
         * stickers a day, and listing 10,000 files costs seconds of work in another process.
         */
        private const val SCAN_COOLDOWN_MILLIS = 10 * 60 * 1000L

        /**
         * Picks up newly saved stickers in the background, once a day; opening the app also
         * checks for them.
         */
        fun schedulePeriodic(context: Context) {
            val constraints = Constraints.Builder().setRequiresBatteryNotLow(true).build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                PERIODIC,
                // UPDATE, not KEEP: phones already scheduled every 6 hours move to daily.
                ExistingPeriodicWorkPolicy.UPDATE,
                PeriodicWorkRequestBuilder<IndexWorker>(24, TimeUnit.HOURS)
                    .setConstraints(constraints)
                    .continueSoon()
                    .build(),
            )
        }
    }
}
