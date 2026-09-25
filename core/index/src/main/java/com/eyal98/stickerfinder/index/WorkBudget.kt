package com.eyal98.stickerfinder.index

import androidx.work.BackoffPolicy
import androidx.work.WorkRequest
import java.util.concurrent.TimeUnit

/**
 * Android stops background work after about 10 minutes. Indexers stop on their own before that,
 * and the worker asks to run again shortly ([Result.retry][androidx.work.ListenableWorker.Result.retry]
 * with a short linear backoff), so a large folder is processed in consecutive runs rather than
 * waiting hours for the next periodic run.
 */
class WorkBudget(private val millis: Long = DEFAULT_MILLIS) {
    private val start = System.nanoTime()

    val exhausted: Boolean get() = (System.nanoTime() - start) / 1_000_000 >= millis

    companion object {
        const val DEFAULT_MILLIS = 8 * 60 * 1000L

        /** How many times a sticker may fail before the step that fails is skipped. */
        const val MAX_ATTEMPTS = 2

        private const val RETRY_SECONDS = 30L

        fun <B : WorkRequest.Builder<B, *>> B.continueSoon(): B =
            setBackoffCriteria(BackoffPolicy.LINEAR, RETRY_SECONDS, TimeUnit.SECONDS)
    }
}
