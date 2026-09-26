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
 * Each run loads the embedding model and may embed thousands of stickers, so updates from the
 * background jobs wait for the charger; the user's own edits (a few stickers) run right away.
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
        private const val NOW = "sticker-embed-now"

        /** Unique work names, for diagnostics. */
        val UNIQUE_NAMES = listOf(NAME, NOW)

        /**
         * Picks up background changes (captions, picture tags, new stickers) the next time the
         * phone is charging, or now if it already is. Runs after the current pass, if any.
         */
        fun runNow(context: Context) = enqueue(context, NAME, Constraints.Builder().setRequiresCharging(true).build())

        /** For the user's own edits, like tags: a few stickers, so it doesn't wait for the charger. */
        fun runForEdit(context: Context) = enqueue(context, NOW, Constraints.Builder().setRequiresBatteryNotLow(true).build())

        private fun enqueue(context: Context, name: String, constraints: Constraints) {
            WorkManager.getInstance(context).enqueueUniqueWork(
                name,
                ExistingWorkPolicy.APPEND_OR_REPLACE,
                OneTimeWorkRequestBuilder<EmbedWorker>().setConstraints(constraints).continueSoon().build(),
            )
        }
    }
}
