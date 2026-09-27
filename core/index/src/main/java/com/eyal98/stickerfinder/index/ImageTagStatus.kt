package com.eyal98.stickerfinder.index

import android.content.Context
import com.eyal98.stickerfinder.data.StickerDao
import com.eyal98.stickerfinder.ml.ModelCrashGuard
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map

/**
 * What picture tagging is doing, in words the user can act on. A tagging run over thousands of
 * stickers takes a long time and only runs on the charger, so "N left" alone looked stuck to
 * testers; this says whether it's running (and roughly how long is left) or why it's paused.
 */
data class ImageTagStatus(
    val phase: Phase,
    /** Stickers still to tag, and all stickers. */
    val left: Int,
    val total: Int,
    /** Estimated time left while running, once there's enough progress to tell; else null. */
    val etaMillis: Long? = null,
) {
    enum class Phase {
        /** Every sticker has picture tags. */
        DONE,
        RUNNING,

        /** Waits for the phone to be plugged in (or "Start now"). */
        WAITING_FOR_CHARGER,

        /** Android holds it back until the battery isn't low. */
        BATTERY_LOW,

        /** Allowed to run, waiting for Android to start it. */
        WAITING_TO_START,

        /** Turned off after the picture model crashed the app. */
        TURNED_OFF,
    }

    val done: Int get() = (total - left).coerceAtLeast(0)

    companion object {
        /** The status now and as it changes. */
        fun observe(context: Context, dao: StickerDao): Flow<ImageTagStatus> {
            val eta = EtaEstimator()
            return combine(
                dao.observeImageTagPendingCount(),
                dao.observeCount(),
                ImageTagWorker.observeRunning(context),
                Power.observe(context),
            ) { left, total, running, power ->
                val phase = when {
                    left == 0 -> Phase.DONE
                    ModelCrashGuard.isDisabled(context, ModelCrashGuard.IMAGE_TAGS) -> Phase.TURNED_OFF
                    running -> Phase.RUNNING
                    power.batteryLow -> Phase.BATTERY_LOW
                    !power.charging -> Phase.WAITING_FOR_CHARGER
                    else -> Phase.WAITING_TO_START
                }
                ImageTagStatus(phase, left, total)
            }.map { status ->
                val millis = if (status.phase == Phase.RUNNING) {
                    eta.update(status.left, System.currentTimeMillis())
                } else {
                    eta.reset()
                    null
                }
                status.copy(etaMillis = millis)
            }
        }
    }
}

/**
 * Estimates the time left from recent progress: stickers done per minute over up to the last
 * [WINDOW_MILLIS]. Says nothing until it has seen [MIN_SPAN_MILLIS] and [MIN_DONE] stickers, so an
 * early burst or a slow first sticker doesn't produce a wild number.
 */
class EtaEstimator {
    private val samples = ArrayDeque<Pair<Long, Int>>()

    fun reset() = samples.clear()

    /** Records [left] at [now]; returns the estimated millis left, or null when unsure. */
    fun update(left: Int, now: Long): Long? {
        // A count going up (new stickers found) makes older samples meaningless.
        if (samples.isNotEmpty() && left > samples.last().second) samples.clear()
        samples.addLast(now to left)
        // Forget samples older than the window, keeping at least two to measure with.
        while (samples.size > 2 && now - samples.first().first > WINDOW_MILLIS) samples.removeFirst()
        val (firstAt, firstLeft) = samples.first()
        val span = now - firstAt
        val done = firstLeft - left
        if (span < MIN_SPAN_MILLIS || done < MIN_DONE) return null
        return (left.toDouble() * span / done).toLong()
    }

    companion object {
        const val WINDOW_MILLIS = 10 * 60 * 1000L
        const val MIN_SPAN_MILLIS = 60 * 1000L
        const val MIN_DONE = 5
    }
}
