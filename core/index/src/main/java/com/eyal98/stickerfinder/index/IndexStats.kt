package com.eyal98.stickerfinder.index

import android.content.Context
import androidx.core.content.edit
import java.util.Locale

/** The speed of the latest indexing run, for the diagnostics report. */
object IndexStats {

    private const val PREFS = "index_stats"

    fun record(context: Context, processed: Int, millis: Long, readers: Int, foreground: Boolean) {
        if (processed == 0) return
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit {
            putInt("processed", processed)
            putLong("millis", millis)
            putInt("readers", readers)
            putBoolean("foreground", foreground)
        }
    }

    fun describe(context: Context): String? {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val processed = prefs.getInt("processed", 0).takeIf { it > 0 } ?: return null
        val seconds = prefs.getLong("millis", 0) / 1000.0
        val perMinute = if (seconds > 0) processed * 60 / seconds else 0.0
        return String.format(
            Locale.US,
            "last run %d stickers in %.0f s (%.0f/min), %d readers, %s",
            processed, seconds, perMinute, prefs.getInt("readers", 0),
            if (prefs.getBoolean("foreground", false)) "foreground" else "background",
        )
    }
}
