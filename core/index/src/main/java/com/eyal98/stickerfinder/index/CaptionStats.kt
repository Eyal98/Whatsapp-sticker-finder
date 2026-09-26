package com.eyal98.stickerfinder.index

import android.content.Context
import androidx.core.content.edit
import java.util.Locale

/** The speed of the latest captioning run and where the model ran, for the diagnostics report. */
object CaptionStats {

    private const val PREFS = "caption_stats"

    fun record(context: Context, processed: Int, millis: Long, setup: String, outcomes: CaptionIndexer.Outcomes) {
        if (processed == 0 && outcomes.errors == 0) return
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit {
            putInt("processed", processed)
            putInt("described", outcomes.described)
            putInt("empty", outcomes.empty)
            putInt("errors", outcomes.errors)
            // Exception text from the runtime; holds no sticker content.
            putString("last_error", outcomes.lastError?.take(MAX_ERROR_LENGTH))
            putLong("millis", millis)
            putString("setup", setup)
            putLong("at", System.currentTimeMillis())
        }
    }

    fun describe(context: Context): String? {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (!prefs.contains("processed")) return null
        val processed = prefs.getInt("processed", 0)
        val seconds = prefs.getLong("millis", 0) / 1000.0
        val perSticker = if (processed > 0) seconds / processed else 0.0
        return String.format(
            Locale.US,
            "last caption run %d stickers in %.0f s (%.1f s each), %s",
            processed, seconds, perSticker, prefs.getString("setup", "?"),
        ) + String.format(
            Locale.US,
            "\n  described %d, empty replies %d, errors %d",
            prefs.getInt("described", 0), prefs.getInt("empty", 0), prefs.getInt("errors", 0),
        ) + prefs.getString("last_error", null)?.let { "\n  last error: $it" }.orEmpty()
    }

    private const val MAX_ERROR_LENGTH = 300
}
