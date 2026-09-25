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
import com.eyal98.stickerfinder.index.WorkBudget.Companion.continueSoon
import com.eyal98.stickerfinder.caption.StickerCaptioners
import com.eyal98.stickerfinder.ml.DeviceCapability
import com.eyal98.stickerfinder.ml.ModelCatalog
import com.eyal98.stickerfinder.ml.ModelCrashGuard
import com.eyal98.stickerfinder.ml.ModelStore
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
        // From here until the finally below, a crash in the model's native code turns it off.
        ModelCrashGuard.markBusy(applicationContext, ModelCrashGuard.CAPTION)
        val captioner = try {
            StickerCaptioners.create(applicationContext, model)
        } catch (e: Exception) {
            // Not a usable model for this runtime: turn it off rather than retry forever.
            Log.w(TAG, "Could not load ${model.displayName}", e)
            ModelCrashGuard.disable(applicationContext, ModelCrashGuard.CAPTION)
            return Result.failure()
        }
        return try {
            val progress = CaptionIndexer(
                applicationContext.contentResolver,
                host.database.stickerDao(),
                host.repository,
                captioner,
            ).captionPending()
            if (progress.processed > 0) EmbedWorker.runNow(applicationContext)
            if (progress.finished) Result.success() else Result.retry()
        } finally {
            captioner.close()
            ModelCrashGuard.clearBusy(applicationContext, ModelCrashGuard.CAPTION)
        }
    }

    companion object {
        private const val TAG = "CaptionWorker"
        private const val NOW = "sticker-caption-now"
        private const val PERIODIC = "sticker-caption-periodic"

        /** Unique work names, for diagnostics. */
        val UNIQUE_NAMES = listOf(NOW, PERIODIC)

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
