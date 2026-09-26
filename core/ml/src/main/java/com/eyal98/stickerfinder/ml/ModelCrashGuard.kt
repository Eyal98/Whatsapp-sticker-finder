package com.eyal98.stickerfinder.ml

import android.app.ActivityManager
import android.app.ApplicationExitInfo
import android.content.Context
import android.util.Log
import androidx.core.content.edit

/**
 * Stops a model that crashes the app from crashing it again and again.
 *
 * On-device models run native code in the app's process: a crash there can't be caught and takes
 * the whole app down, and the background job that loaded the model would load it again on the
 * next run. So a flag is written (synchronously) while a model is in use; if the app then dies
 * from a crash with the flag still set, the next start turns that model off. The user can turn it
 * on again or remove it from the Smart search screen.
 */
object ModelCrashGuard {

    const val EMBEDDING = "embedding"
    const val IMAGE_TAGS = "image_tags"
    const val FACES = "faces"
    val FEATURES = listOf(EMBEDDING, IMAGE_TAGS, FACES)

    private const val TAG = "ModelCrashGuard"
    private const val PREFS = "model_crash_guard"
    private const val KEY_INITIALIZED = "initialized"
    private const val MAX_REASON = 600

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** Call when a model is loaded or about to run. Written to disk before returning. */
    fun markBusy(context: Context, feature: String) =
        prefs(context).edit(commit = true) { putBoolean("busy_$feature", true) }

    fun clearBusy(context: Context, feature: String) =
        prefs(context).edit(commit = true) { remove("busy_$feature") }

    fun isDisabled(context: Context, feature: String): Boolean =
        prefs(context).getBoolean("disabled_$feature", false)

    /**
     * Turns a feature off, e.g. because its model failed to load. [reason] is kept for the
     * diagnostics report.
     */
    fun disable(context: Context, feature: String, reason: String? = null) =
        prefs(context).edit(commit = true) {
            putBoolean("disabled_$feature", true)
            remove("busy_$feature")
            if (reason != null) putString("reason_$feature", reason.take(MAX_REASON)) else remove("reason_$feature")
        }

    /** Why [feature] was turned off, if known. */
    fun reason(context: Context, feature: String): String? = prefs(context).getString("reason_$feature", null)

    /** "IllegalStateException: message", with the causes and suppressed failures, for [disable]. */
    fun describe(error: Throwable): String {
        val seen = HashSet<Throwable>()
        fun line(t: Throwable) = "${t.javaClass.simpleName}: ${t.message.orEmpty().lineSequence().firstOrNull().orEmpty()}"
        val parts = mutableListOf<String>()
        fun walk(t: Throwable?) {
            if (t == null || !seen.add(t) || parts.size >= 6) return
            parts += line(t)
            t.suppressed.forEach(::walk)
            walk(t.cause)
        }
        walk(error)
        return parts.joinToString(" <- ")
    }

    /** Turns a feature back on (user asked, or a new model file was installed). */
    fun enable(context: Context, feature: String) =
        prefs(context).edit(commit = true) {
            remove("disabled_$feature")
            remove("busy_$feature")
            remove("reason_$feature")
        }

    fun crashCount(context: Context, feature: String): Int = prefs(context).getInt("crashes_$feature", 0)

    /**
     * Call once when the app process starts. Turns off any model that was in use when the
     * previous process crashed. Being killed for other reasons (swiped away, low memory, update)
     * doesn't count.
     */
    fun onProcessStart(context: Context, installedFeatures: Set<String>) {
        val prefs = prefs(context)
        val lastExit = context.getSystemService(ActivityManager::class.java)
            .getHistoricalProcessExitReasons(context.packageName, 0, 1)
            .firstOrNull()
        val crashed = lastExit?.reason == ApplicationExitInfo.REASON_CRASH ||
            lastExit?.reason == ApplicationExitInfo.REASON_CRASH_NATIVE

        // First start with this guard: nothing was marked busy by earlier versions, so if the app
        // last died from a crash, assume an installed model may have caused it.
        val suspects = if (!prefs.getBoolean(KEY_INITIALIZED, false)) {
            if (crashed) installedFeatures else emptySet()
        } else {
            FEATURES.filterTo(HashSet()) { prefs.getBoolean("busy_$it", false) }
        }

        prefs.edit(commit = true) {
            putBoolean(KEY_INITIALIZED, true)
            for (feature in suspects) {
                remove("busy_$feature")
                if (crashed) {
                    Log.w(TAG, "The app crashed while the $feature model was in use; turning it off")
                    putBoolean("disabled_$feature", true)
                    putString("reason_$feature", "the app crashed while it was in use")
                    putInt("crashes_$feature", prefs.getInt("crashes_$feature", 0) + 1)
                }
            }
        }
    }
}
