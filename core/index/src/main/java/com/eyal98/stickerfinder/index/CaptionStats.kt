package com.eyal98.stickerfinder.index

import android.content.Context
import androidx.core.content.edit
import java.util.Locale

/** The speed of the latest captioning run and where the model ran, for the diagnostics report. */
object CaptionStats {

    private const val PREFS = "caption_stats"

    fun record(context: Context, processed: Int, millis: Long, setup: String) {
        if (processed == 0) return
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit {
            putInt("processed", processed)
            putLong("millis", millis)
            putString("setup", setup)
            putLong("at", System.currentTimeMillis())
        }
    }

    fun describe(context: Context): String? {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val processed = prefs.getInt("processed", 0).takeIf { it > 0 } ?: return null
        val seconds = prefs.getLong("millis", 0) / 1000.0
        val perSticker = if (processed > 0) seconds / processed else 0.0
        return String.format(
            Locale.US,
            "last caption run %d stickers in %.0f s (%.1f s each), %s",
            processed, seconds, perSticker, prefs.getString("setup", "?"),
        )
    }
}
