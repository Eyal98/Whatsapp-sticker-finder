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
import com.eyal98.stickerfinder.caption.DeviceCapability
import com.eyal98.stickerfinder.caption.MediaPipeCaptioner
import com.eyal98.stickerfinder.caption.ModelStore
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
        val model = ModelStore.installed(applicationContext) ?: return Result.success()
        if (!DeviceCapability.canRun(applicationContext, model.model)) {
            Log.w(TAG, "Not enough memory for ${model.displayName}")
            return Result.failure()
        }
        val captioner = try {
            MediaPipeCaptioner.create(applicationContext, model)
        } catch (e: RuntimeException) {
            Log.w(TAG, "Could not load ${model.displayName}", e)
            return Result.failure()
        }
        return try {
            CaptionIndexer(
                applicationContext.contentResolver,
                host.database.stickerDao(),
                host.repository,
                captioner,
            ).captionPending()
            Result.success()
        } finally {
            captioner.close()
        }
    }

    companion object {
        private const val TAG = "CaptionWorker"
        private const val NOW = "sticker-caption-now"
        private const val PERIODIC = "sticker-caption-periodic"

        /** "Start now" from the settings screen: still waits for the charger. */
        fun runNow(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiresCharging(true)
                .setRequiresStorageNotLow(true)
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(
                NOW,
                ExistingWorkPolicy.KEEP,
                OneTimeWorkRequestBuilder<CaptionWorker>().setConstraints(constraints).build(),
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
