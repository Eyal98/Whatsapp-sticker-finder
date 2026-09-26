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
import kotlinx.coroutines.flow.first

/**
 * Brings meaning vectors up to date after stickers' text changes (new captions, OCR, tags). First
 * refreshes learned tags ([LearnedTagger]), since they are part of that text.
 * Each run loads the embedding model and may embed thousands of stickers, so updates from the
 * background jobs wait for the charger; the user's own edits (a few stickers) run right away.
 */
class EmbedWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val host = applicationContext as StickerIndexHost
        // Learned tags first: they change stickers' text, which this pass then embeds.
        try {
            LearnedTagger(applicationContext, host.database.stickerDao()).refresh()
        } catch (e: Exception) {
            Log.w(TAG, "Could not update learned tags", e)
        }
        if (!host.embedders.isAvailable()) return Result.success()
        val progress = EmbedIndexer(host.database.stickerDao(), host.embedders).embedPending()
        return if (progress == null || progress.finished) Result.success() else Result.retry()
    }

    companion object {
        private const val TAG = "EmbedWorker"
        private const val NAME = "sticker-embed"
        private const val NOW = "sticker-embed-now"

        /** Unique work names, for diagnostics. */
        val UNIQUE_NAMES = listOf(NAME, NOW)

        /**
         * Picks up background changes (picture tags, new stickers) the next time the phone is
         * charging, or now if it already is.
         */
        suspend fun runNow(context: Context) = enqueue(context, NAME, Constraints.Builder().setRequiresCharging(true).build())

        /** For the user's own edits, like tags: a few stickers, so it doesn't wait for the charger. */
        suspend fun runForEdit(context: Context) = enqueue(context, NOW, Constraints.Builder().setRequiresBatteryNotLow(true).build())

        /**
         * At most one pass waits at a time: each pass checks every sticker, so a waiting one
         * already covers any later change. Appending every request instead built chains of ~70
         * passes (diagnostics, build 119). A change made while a pass runs gets one pass after it,
         * since the running one may have read that sticker already.
         */
        private suspend fun enqueue(context: Context, name: String, constraints: Constraints) {
            val workManager = WorkManager.getInstance(context)
            val states = workManager.getWorkInfosForUniqueWorkFlow(name).first().map { it.state }
            if (states.any { it == WorkInfo.State.ENQUEUED || it == WorkInfo.State.BLOCKED }) return
            workManager.enqueueUniqueWork(
                name,
                if (WorkInfo.State.RUNNING in states) ExistingWorkPolicy.APPEND_OR_REPLACE else ExistingWorkPolicy.REPLACE,
                OneTimeWorkRequestBuilder<EmbedWorker>().setConstraints(constraints).continueSoon().build(),
            )
        }
    }
}
