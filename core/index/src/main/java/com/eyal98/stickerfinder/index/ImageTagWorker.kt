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
import com.eyal98.stickerfinder.ml.ModelCatalog
import com.eyal98.stickerfinder.ml.ModelCrashGuard
import com.eyal98.stickerfinder.vision.PictureLabels
import com.eyal98.stickerfinder.vision.SiglipImageEncoder
import com.eyal98.stickerfinder.vision.SiglipModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.io.IOException

/**
 * Tags stickers with the SigLIP2 image model (see [ImageTagger]). About a third of a second per
 * sticker on the CPU: minutes of full CPU for a big backlog, so it waits for the charger unless
 * the user starts it. Started from the app it runs in the foreground with a notification;
 * otherwise in short background slices.
 */
class ImageTagWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val host = applicationContext as StickerIndexHost
        // Bundled in the app; only a build made without it (e.g. offline) lacks it.
        if (!SiglipModel.isBundled(applicationContext)) return Result.success()
        if (ModelCrashGuard.isDisabled(applicationContext, ModelCrashGuard.IMAGE_TAGS)) return Result.success()
        val labels = try {
            PictureLabels.load(applicationContext)
        } catch (e: IOException) {
            Log.w(TAG, "Picture labels missing", e)
            return Result.failure()
        } catch (e: IllegalArgumentException) {
            Log.w(TAG, "Picture labels don't match", e)
            return Result.failure()
        }
        val dao = host.database.stickerDao()
        val tagger = ImageTagger(applicationContext.contentResolver, dao, labels, ModelCatalog.SIGLIP2_B16.id)

        val foreground = tryForeground(pendingCount())
        val budget = WorkBudget(if (foreground) FOREGROUND_BUDGET_MILLIS else WorkBudget.DEFAULT_MILLIS)
        // A new label list: re-derive tags from the stored vectors first. Doesn't need the model.
        if (!tagger.retagOld(budget)) return Result.retry()
        if (pendingCount() == 0) return Result.success()

        // From here until the finally below, a crash in the model's native code turns it off.
        ModelCrashGuard.markBusy(applicationContext, ModelCrashGuard.IMAGE_TAGS)
        val encoder = try {
            SiglipImageEncoder(SiglipModel.map(applicationContext))
        } catch (e: Exception) {
            Log.w(TAG, "Could not load the SigLIP 2 model", e)
            ModelCrashGuard.disable(applicationContext, ModelCrashGuard.IMAGE_TAGS, ModelCrashGuard.describe(e))
            return Result.failure()
        }
        return try {
            val progress = tagger.tagPending(encoder, budget) { done ->
                if (foreground && done % NOTIFY_EVERY == 0) tryForeground(pendingCount())
            }
            // New tags change what stickers mean for semantic search.
            if (progress.processed > 0) EmbedWorker.runNow(applicationContext)
            if (progress.finished) Result.success() else Result.retry()
        } finally {
            encoder.close()
            ModelCrashGuard.clearBusy(applicationContext, ModelCrashGuard.IMAGE_TAGS)
        }
    }

    private suspend fun pendingCount(): Int =
        (applicationContext as StickerIndexHost).database.stickerDao().observeImageTagPendingCount().first()

    private suspend fun tryForeground(left: Int): Boolean =
        try {
            setForeground(
                IndexNotification.foregroundInfo(
                    applicationContext, left, IndexNotification.IMAGE_TAGS_ID, R.string.image_tag_notification_title,
                ),
            )
            true
        } catch (e: IllegalStateException) {
            // ForegroundServiceStartNotAllowedException: the app isn't in the foreground.
            false
        } catch (e: SecurityException) {
            false
        }

    companion object {
        private const val TAG = "ImageTagWorker"
        private const val NAME = "sticker-image-tags"
        private const val NOTIFY_EVERY = 20
        private const val FOREGROUND_BUDGET_MILLIS = 2 * 60 * 60 * 1000L

        /** Unique work names, for diagnostics. */
        val UNIQUE_NAMES = listOf(NAME)

        private fun request(whileCharging: Boolean) = OneTimeWorkRequestBuilder<ImageTagWorker>()
            .setConstraints(
                Constraints.Builder().setRequiresBatteryNotLow(true).setRequiresCharging(whileCharging).build(),
            )
            .continueSoon()
            .build()

        /**
         * Starts now, from the app, so it can run in the foreground: when the user asks for it,
         * or while charging. A run waiting out its retry delay is replaced; a running one is kept.
         */
        suspend fun startNow(context: Context) {
            if (!SiglipModel.isBundled(context)) return
            val workManager = WorkManager.getInstance(context)
            val running = workManager.getWorkInfosForUniqueWorkFlow(NAME).first()
                .any { it.state == WorkInfo.State.RUNNING }
            workManager.enqueueUniqueWork(
                NAME,
                if (running) ExistingWorkPolicy.KEEP else ExistingWorkPolicy.REPLACE,
                request(whileCharging = false),
            )
        }

        /**
         * Picks up newly indexed stickers the next time the phone charges, unless a run is
         * already queued or running.
         */
        fun runNow(context: Context) {
            if (!SiglipModel.isBundled(context)) return
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
