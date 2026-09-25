package com.eyal98.stickerfinder.index

import android.content.Context
import android.util.Log
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.eyal98.stickerfinder.index.WorkBudget.Companion.continueSoon
import com.eyal98.stickerfinder.caption.StickerCaptioners
import com.eyal98.stickerfinder.ml.DeviceCapability
import com.eyal98.stickerfinder.ml.ModelCatalog
import com.eyal98.stickerfinder.ml.ModelCrashGuard
import com.eyal98.stickerfinder.ml.ModelStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import java.util.concurrent.TimeUnit

/**
 * Describes stickers with the on-device model. Heavy on CPU/GPU, memory and battery, so it only
 * runs while charging (and, for the automatic runs, while the phone is idle). Work that hits
 * WorkManager's time limit is stopped and continues on the next run; each caption is saved as
 * soon as it's done.
 */
class CaptionWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val host = applicationContext as StickerIndexHost
        val model = ModelStore.CAPTION.installed(applicationContext) ?: return Result.success()
        // Turned off after it crashed the app (see ModelCrashGuard), until the user turns it on.
        if (ModelCrashGuard.isDisabled(applicationContext, ModelCrashGuard.CAPTION)) return Result.success()
        if (!DeviceCapability.canRun(applicationContext, model.model, ModelCatalog.GEMMA_3N_E2B)) {
            Log.w(TAG, "Not enough memory for ${model.displayName}")
            return Result.failure()
        }
        val dao = host.database.stickerDao()
        // From here until the finally below, a crash in the model's native code turns it off.
        ModelCrashGuard.markBusy(applicationContext, ModelCrashGuard.CAPTION)
        report(done = null, left = dao.observeCaptionPendingCount().first())
        val captioner = try {
            StickerCaptioners.create(applicationContext, model)
        } catch (e: Exception) {
            // Not a usable model for this runtime: turn it off rather than retry forever.
            Log.w(TAG, "Could not load ${model.displayName}", e)
            ModelCrashGuard.disable(applicationContext, ModelCrashGuard.CAPTION)
            CaptionNotification.cancel(applicationContext)
            return Result.failure()
        }
        return try {
            report(done = 0, left = dao.observeCaptionPendingCount().first())
            val progress = CaptionIndexer(
                applicationContext.contentResolver,
                dao,
                host.repository,
                captioner,
            ).captionPending { done -> report(done, dao.observeCaptionPendingCount().first()) }
            if (progress.processed > 0) EmbedWorker.runNow(applicationContext)
            if (progress.finished) Result.success() else Result.retry()
        } finally {
            captioner.close()
            ModelCrashGuard.clearBusy(applicationContext, ModelCrashGuard.CAPTION)
            CaptionNotification.cancel(applicationContext)
        }
    }

    /** Updates the notification and the progress the app shows; [done] is null while loading. */
    private suspend fun report(done: Int?, left: Int) {
        setProgress(workDataOf(KEY_DONE to (done ?: -1)))
        CaptionNotification.show(applicationContext, done, left)
    }

    companion object {
        private const val TAG = "CaptionWorker"
        private const val NOW = "sticker-caption-now"
        private const val PERIODIC = "sticker-caption-periodic"

        /** Unique work names, for diagnostics. */
        val UNIQUE_NAMES = listOf(NOW, PERIODIC)

        private const val KEY_DONE = "done"

        fun observeStatus(context: Context): Flow<CaptionStatus> {
            val workManager = WorkManager.getInstance(context)
            return combine(
                workManager.getWorkInfosForUniqueWorkFlow(NOW),
                workManager.getWorkInfosForUniqueWorkFlow(PERIODIC),
            ) { now, periodic ->
                val infos = now + periodic
                val running = infos.firstOrNull { it.state == WorkInfo.State.RUNNING }
                when {
                    running != null -> running.progress.getInt(KEY_DONE, -1)
                        .let { if (it < 0) CaptionStatus.Loading else CaptionStatus.Describing(it) }
                    now.any { it.state == WorkInfo.State.ENQUEUED } -> CaptionStatus.Waiting
                    else -> CaptionStatus.Idle
                }
            }
        }

        /** "Start now" from the settings screen: still waits for the charger. */
        fun runNow(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiresCharging(true)
                .setRequiresStorageNotLow(true)
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(
                NOW,
                ExistingWorkPolicy.KEEP,
                OneTimeWorkRequestBuilder<CaptionWorker>().setConstraints(constraints).continueSoon().build(),
            )
        }

        /** Overnight-style background captioning: charging, idle, battery and storage OK. */
        fun schedulePeriodic(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiresCharging(true)
                .setRequiresDeviceIdle(true)
                .setRequiresBatteryNotLow(true)
                .setRequiresStorageNotLow(true)
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                PERIODIC,
                ExistingPeriodicWorkPolicy.KEEP,
                PeriodicWorkRequestBuilder<CaptionWorker>(1, TimeUnit.HOURS)
                    .setConstraints(constraints)
                    .continueSoon()
                    .build(),
            )
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).apply {
                cancelUniqueWork(NOW)
                cancelUniqueWork(PERIODIC)
            }
        }
    }
}

/** What captioning is doing right now, for the app's status line. */
sealed interface CaptionStatus {
    data object Idle : CaptionStatus

    /** Queued: waiting for the charger (and, for automatic runs, for the phone to be idle). */
    data object Waiting : CaptionStatus

    data object Loading : CaptionStatus

    /** [done] stickers described in the current run. */
    data class Describing(val done: Int) : CaptionStatus
}
