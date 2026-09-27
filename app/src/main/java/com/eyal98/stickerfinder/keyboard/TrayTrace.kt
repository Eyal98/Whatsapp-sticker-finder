package com.eyal98.stickerfinder.keyboard

import android.content.Context
import androidx.core.content.edit
import java.util.Locale

/**
 * What happened in the last few sends from WhatsApp's sticker tray, for problem reports: which
 * step worked and how (WhatsApp's view ids), how well the best cell matched, and why an attempt
 * gave up. WhatsApp's layout changes between versions; this is how a failing step gets fixed.
 * Never text: no pack names, labels, messages or contact names.
 */
internal object TrayTrace {

    private const val PREFS = "tray_trace"
    private const val KEY = "attempts"
    private const val KEEP = 5

    class Builder {
        private val parts = mutableListOf<String>()
        private var failure: String? = null

        fun step(name: String, how: String) {
            parts += "$name: $how"
        }

        fun page(index: Int, cells: Int, best: Float, second: Float?) {
            parts += String.format(
                Locale.US,
                "page %d: %d cells, best %.2f%s",
                index + 1,
                cells,
                best,
                second?.let { String.format(Locale.US, ", next %.2f", it) }.orEmpty(),
            )
        }

        fun fail(reason: String, clickableIds: List<String>) {
            failure = reason + if (clickableIds.isEmpty()) "" else " (tappable ids: ${clickableIds.joinToString(" ")})"
        }

        private var outcome = ""

        fun finish(sent: Boolean, millis: Long) {
            outcome = if (sent) "sent in ${millis} ms" else "gave up after ${millis} ms: $failure"
        }

        fun build(): String = (parts + outcome).joinToString("; ")
    }

    fun save(context: Context, attempt: String) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val kept = read(context).takeLast(KEEP - 1) + attempt
        prefs.edit { putString(KEY, kept.joinToString("\n")) }
    }

    /** The last attempts, oldest first. */
    fun read(context: Context): List<String> =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null)
            ?.split('\n')?.filter { it.isNotBlank() }.orEmpty()
}
