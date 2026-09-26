package com.eyal98.stickerfinder.index

import android.content.Context
import android.util.Log
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.eyal98.stickerfinder.index.WorkBudget.Companion.continueSoon
import com.eyal98.stickerfinder.ml.ModelCrashGuard
import com.eyal98.stickerfinder.vision.StickerFaces
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/**
 * Finds faces on stickers and groups them into people (see [FaceScanner], [FaceGrouper]). Only
 * when the user turned People on. Like picture tagging: waits for the charger unless started
 * from the app.
 */
class FaceWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val context = applicationContext
        if (!FaceSettings.isEnabled(context) || !StickerFaces.isBundled(context)) return Result.success()
        if (ModelCrashGuard.isDisabled(context, ModelCrashGuard.FACES)) return Result.success()
        val dao = (context as StickerIndexHost).database.stickerDao()
        val pending = dao.observeFaceScanPendingCount().first()
        if (pending == 0) {
            if (FaceGrouper.regroup(context, dao) > 0) EmbedWorker.runNow(context)
            return Result.success()
        }
        val foreground = tryForeground(pending)
        val budget = WorkBudget(if (foreground) FOREGROUND_BUDGET_MILLIS else WorkBudget.DEFAULT_MILLIS)

        // From here until the finally below, a crash in the models' native code turns People off.
        ModelCrashGuard.markBusy(context, ModelCrashGuard.FACES)
        val faces = try {
            StickerFaces(StickerFaces.mapModel(context))
        } catch (e: Exception) {
            Log.w(TAG, "Could not load the face models", e)
            ModelCrashGuard.disable(context, ModelCrashGuard.FACES, ModelCrashGuard.describe(e))
            return Result.failure()
        }
        return try {
            val progress = FaceScanner(context.contentResolver, dao, faces).scanPending(budget) { done ->
                if (foreground && done % NOTIFY_EVERY == 0) tryForeground(dao.observeFaceScanPendingCount().first())
            }
            // Group what was found so far, even if the run stopped early: groups show up sooner.
            val renamed = FaceGrouper.regroup(context, dao)
            if (renamed > 0) EmbedWorker.runNow(context)
            if (progress.finished) Result.success() else Result.retry()
        } finally {
            faces.close()
            ModelCrashGuard.clearBusy(context, ModelCrashGuard.FACES)
        }
    }

    private suspend fun tryForeground(left: Int): Boolean =
        try {
            setForeground(
                IndexNotification.foregroundInfo(applicationContext, left, IndexNotification.FACES_ID, R.string.face_notification_title),
            )
            true
        } catch (e: IllegalStateException) {
            false
        } catch (e: SecurityException) {
            false
        }

    companion object {
        private const val TAG = "FaceWorker"
        private const val NAME = "sticker-faces"
        private const val NOTIFY_EVERY = 20
        private const val FOREGROUND_BUDGET_MILLIS = 2 * 60 * 60 * 1000L

        /** Unique work names, for diagnostics. */
        val UNIQUE_NAMES = listOf(NAME)

        private fun request(whileCharging: Boolean) = OneTimeWorkRequestBuilder<FaceWorker>()
            .setConstraints(Constraints.Builder().setRequiresBatteryNotLow(true).setRequiresCharging(whileCharging).build())
            .continueSoon()
            .build()

        /** From the app (Start now, or right after turning People on): runs in the foreground. */
        suspend fun startNow(context: Context) {
            if (!FaceSettings.isEnabled(context) || !StickerFaces.isBundled(context)) return
            val workManager = WorkManager.getInstance(context)
            val running = workManager.getWorkInfosForUniqueWorkFlow(NAME).first().any { it.state == WorkInfo.State.RUNNING }
            workManager.enqueueUniqueWork(
                NAME,
                if (running) ExistingWorkPolicy.KEEP else ExistingWorkPolicy.REPLACE,
                request(whileCharging = false),
            )
        }

        /** New stickers: scanned the next time the phone charges. */
        fun runNow(context: Context) {
            if (!FaceSettings.isEnabled(context) || !StickerFaces.isBundled(context)) return
            WorkManager.getInstance(context).enqueueUniqueWork(NAME, ExistingWorkPolicy.KEEP, request(whileCharging = true))
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(NAME)
        }

        fun observeRunning(context: Context): Flow<Boolean> =
            WorkManager.getInstance(context).getWorkInfosForUniqueWorkFlow(NAME)
                .map { infos -> infos.any { it.state == WorkInfo.State.RUNNING } }
    }
}
