package com.eyal98.stickerfinder.keyboard

import android.content.Context
import android.os.Binder
import androidx.core.content.edit
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * What happened to the last few stickers sent from the keyboard, for the problem report: whether
 * the app took it, and how that app read the file afterwards (how often, when, from which app,
 * with what error). Numbers and yes/no only: never the sticker, its name or its content hash,
 * which is kept only to tell sends apart and is left out of [describe].
 */
object SendLog {

    private const val PREFS = "send_log"
    private const val KEY = "sends"
    private const val KEEP = 6

    /** A send is starting: [file] is the shared copy's name, [targetUid] the receiving app's. */
    @Synchronized
    fun started(
        context: Context,
        file: String,
        animated: Boolean,
        mimeType: String,
        bytes: Long,
        targetUid: Int,
        shrink: StickerSender.Shrink,
        originalBytes: Long,
    ) {
        val entries = load(context).filterNot { it.optString("file") == file }.toMutableList()
        entries += JSONObject()
            .put("file", file)
            .put("at", System.currentTimeMillis())
            .put("animated", animated)
            .put("mime", mimeType)
            .put("bytes", bytes)
            .put("targetUid", targetUid)
            .put("shrink", shrink.name.lowercase())
            .put("originalBytes", originalBytes)
        save(context, entries.takeLast(KEEP))
    }

    @Synchronized
    fun committed(context: Context, file: String, accepted: Boolean) = update(context, file) { e ->
        e.put("accepted", accepted).put("committedMs", sinceStart(e))
    }

    /** The keyboard went away (switched back, or the user left the chat). */
    @Synchronized
    fun keyboardLeft(context: Context, file: String) = update(context, file) { e ->
        if (!e.has("leftMs")) e.put("leftMs", sinceStart(e))
    }

    /** Called by [SentStickerProvider] when an app opens or asks about a shared copy. */
    @Synchronized
    fun accessed(context: Context, file: String, kind: String, mode: String?, error: Throwable?) = update(context, file) { e ->
        val uid = Binder.getCallingUid()
        val ms = sinceStart(e)
        e.put("$kind.count", e.optInt("$kind.count") + 1)
        if (!e.has("$kind.firstMs")) e.put("$kind.firstMs", ms)
        e.put("$kind.lastMs", ms)
        val target = e.optInt("targetUid", -1)
        if (target >= 0 && uid != target) e.put("$kind.otherApp", e.optInt("$kind.otherApp") + 1)
        if (mode != null && mode != "r") e.put("$kind.notReadOnly", mode)
        if (error != null) {
            e.put("$kind.errors", e.optInt("$kind.errors") + 1)
            e.put("$kind.error", error.javaClass.simpleName)
        }
    }

    /** One line per send, oldest first. */
    fun describe(context: Context): String {
        val entries = load(context)
        if (entries.isEmpty()) return "no sends recorded"
        val format = SimpleDateFormat("MM-dd HH:mm:ss", Locale.US)
        return entries.joinToString("\n") { e ->
            buildString {
                append(format.format(Date(e.optLong("at"))))
                append(if (e.optBoolean("animated")) " animated" else " static")
                append(", ").append(e.optString("mime")).append(", ").append(e.optLong("bytes") / 1024).append(" KB")
                when (e.optString("shrink")) {
                    "shrunk" -> append(" (shrunk from ").append(e.optLong("originalBytes") / 1024).append(" KB)")
                    "failed" -> append(" (too big, couldn't shrink)")
                    "cached" -> append(" (shrunk before)")
                }
                append(", accepted ").append(if (e.has("accepted")) e.optBoolean("accepted") else "?")
                if (e.has("committedMs")) append(" at +").append(e.optLong("committedMs")).append(" ms")
                if (e.optInt("targetUid", -1) < 0) append(", receiving app unknown")
                append(", keyboard left ").append(if (e.has("leftMs")) "+${e.optLong("leftMs")} ms" else "no")
                for (kind in listOf("open", "query", "type")) {
                    val count = e.optInt("$kind.count")
                    append("; $kind $count")
                    if (count == 0) continue
                    append(" (+").append(e.optLong("$kind.firstMs")).append("..+").append(e.optLong("$kind.lastMs")).append(" ms")
                    e.optInt("$kind.otherApp").takeIf { it > 0 }?.let { append(", from another app ").append(it) }
                    e.optString("$kind.notReadOnly").takeIf { it.isNotEmpty() }?.let { append(", mode ").append(it) }
                    e.optInt("$kind.errors").takeIf { it > 0 }?.let { append(", errors ").append(it).append(" ").append(e.optString("$kind.error")) }
                    append(")")
                }
            }
        }
    }

    private fun sinceStart(e: JSONObject) = System.currentTimeMillis() - e.optLong("at")

    private fun update(context: Context, file: String, change: (JSONObject) -> Unit) {
        val entries = load(context)
        val entry = entries.lastOrNull { it.optString("file") == file } ?: return
        change(entry)
        save(context, entries)
    }

    private fun load(context: Context): List<JSONObject> = try {
        val array = JSONArray(prefs(context).getString(KEY, "[]"))
        (0 until array.length()).mapNotNull { array.optJSONObject(it) }
    } catch (e: org.json.JSONException) {
        emptyList()
    }

    private fun save(context: Context, entries: List<JSONObject>) =
        prefs(context).edit { putString(KEY, JSONArray(entries).toString()) }

    private fun prefs(context: Context) = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
