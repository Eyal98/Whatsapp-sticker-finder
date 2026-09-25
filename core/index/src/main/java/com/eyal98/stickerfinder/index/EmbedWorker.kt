package com.eyal98.stickerfinder.index

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.eyal98.stickerfinder.index.WorkBudget.Companion.continueSoon

/**
 * Brings meaning vectors up to date after stickers' text changes (new captions, OCR, tags).
 * Embedding is light next to captioning, so it only waits for a healthy battery.
 */
class EmbedWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val host = applicationContext as StickerIndexHost
        if (!host.embedders.isAvailable()) return Result.success()
        val progress = EmbedIndexer(host.database.stickerDao(), host.embedders).embedPending()
        return if (progress == null || progress.finished) Result.success() else Result.retry()
    }

    companion object {
        private const val NAME = "sticker-embed"

        /** Runs after the current embedding pass (if any), so every change gets picked up. */
        fun runNow(context: Context) {
            WorkManager.getInstance(context).enqueueUniqueWork(
                NAME,
                ExistingWorkPolicy.APPEND_OR_REPLACE,
                OneTimeWorkRequestBuilder<EmbedWorker>()
                    .setConstraints(Constraints.Builder().setRequiresBatteryNotLow(true).build())
                    .continueSoon()
                    .build(),
            )
        }
    }
}
