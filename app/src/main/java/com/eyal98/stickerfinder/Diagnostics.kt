package com.eyal98.stickerfinder

import android.app.ActivityManager
import android.app.ApplicationExitInfo
import android.content.ComponentName
import android.content.Context
import android.content.pm.ApplicationInfo
import android.os.Build
import android.view.inputmethod.InputMethodManager
import androidx.work.WorkManager
import com.eyal98.stickerfinder.data.IndexVersion
import com.eyal98.stickerfinder.index.EmbedWorker
import com.eyal98.stickerfinder.index.ImageTagWorker
import com.eyal98.stickerfinder.index.IndexStats
import com.eyal98.stickerfinder.index.IndexWorker
import com.eyal98.stickerfinder.index.StickerFolder
import com.eyal98.stickerfinder.keyboard.StickerKeyboardService
import com.eyal98.stickerfinder.ml.DeviceCapability
import com.eyal98.stickerfinder.ml.ModelCrashGuard
import com.eyal98.stickerfinder.ml.ModelStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import com.eyal98.stickerfinder.vision.SiglipModel

/**
 * A plain-text report for troubleshooting, shown to the user in full before they share it. It
 * holds counts, states and the app's own warning/error log lines, never sticker content: no
 * images, file names, printed text, captions, tags or searches. Paths and URIs are redacted.
 */
object Diagnostics {

    private const val LOG_LINES = 150
    private const val MAX_CRASH_CHARS = 12_000

    suspend fun build(app: StickerFinderApp): String = withContext(Dispatchers.IO) {
        buildString {
            appendLine("Sticker Finder diagnostics, ${timestamp(System.currentTimeMillis())}")
            section("App") {
                val info = app.packageManager.getPackageInfo(app.packageName, 0)
                val debuggable = app.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0
                appendLine("version ${info.versionName} (${info.longVersionCode})${if (debuggable) " debug" else ""}")
                appendLine("Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT}), ${Build.MANUFACTURER} ${Build.MODEL}")
                appendLine("RAM ${gb(DeviceCapability.totalRamBytes(app))}, free storage ${gb(app.noBackupFilesDir.usableSpace)}")
                appendLine("folder granted: ${StickerFolder.current(app) != null}")
                appendLine("keyboard enabled: ${keyboardEnabled(app)}")
                appendLine("similarity cut-off: ${app.searchSettings.minSimilarity}")
            }
            section("Models") {
                appendLine("embedding: ${ModelStore.EMBEDDING.installed(app)?.displayName ?: "none"}")
                appendLine("picture model bundled: ${SiglipModel.isBundled(app)}")
                for (feature in ModelCrashGuard.FEATURES) {
                    appendLine(
                        "$feature: turned off after crash ${ModelCrashGuard.isDisabled(app, feature)}, " +
                            "crashes ${ModelCrashGuard.crashCount(app, feature)}",
                    )
                    ModelCrashGuard.reason(app, feature)?.takeIf { ModelCrashGuard.isDisabled(app, feature) }
                        ?.let { appendLine("  why: ${redact(it)}") }
                }
            }
            section("Index") {
                val c = app.database.stickerDao().diagnosticCounts(IndexVersion.CURRENT)
                appendLine("stickers ${c.total}, indexed ${c.indexedCount}, pending ${c.total - c.indexedCount}")
                appendLine("failing now ${c.failingNow}, needed retries ${c.retried}, most attempts ${c.maxAttempts}, undecodable ${c.undecodable}")
                appendLine("with printed text ${c.withText}, animated ${c.animated}, starred ${c.starred}")
                appendLine("with pack name ${c.withPackName}, with pack emojis ${c.withEmojis}")
                appendLine("old Gemma descriptions ${c.withCaption}, vectors ${c.vectors}")
                appendLine("picture-tagged ${c.imageTagged} (with tags ${c.withImageTags}, retried ${c.imageTagRetried})")
                IndexStats.describe(app)?.let(::appendLine)
            }
            section("Background work") {
                val workManager = WorkManager.getInstance(app)
                for (name in IndexWorker.UNIQUE_NAMES + ImageTagWorker.UNIQUE_NAMES + EmbedWorker.UNIQUE_NAMES) {
                    val infos = workManager.getWorkInfosForUniqueWorkFlow(name).first()
                    if (infos.isEmpty()) {
                        appendLine("$name: not scheduled")
                    } else {
                        infos.forEach { appendLine("$name: ${it.state}, attempts ${it.runAttemptCount}, stop reason ${it.stopReason}") }
                    }
                }
            }
            section("Recent exits") {
                val exits = app.getSystemService(ActivityManager::class.java)
                    .getHistoricalProcessExitReasons(app.packageName, 0, 8)
                if (exits.isEmpty()) appendLine("none recorded")
                exits.forEach { e ->
                    appendLine(
                        "${timestamp(e.timestamp)} ${exitReason(e.reason)} (status ${e.status}, importance ${e.importance}, " +
                            "pss ${e.pss / 1024} MB)${e.description?.let { d -> ": " + redact(d) } ?: ""}",
                    )
                }
            }
            section("Last crash") {
                appendLine(CrashLog.read(app) ?: "none recorded")
            }
            section("Log (warnings and errors)") {
                appendLine(readLog())
            }
        }
    }

    private inline fun StringBuilder.section(title: String, body: StringBuilder.() -> Unit) {
        appendLine()
        appendLine("== $title ==")
        try {
            body()
        } catch (e: Exception) {
            appendLine("(unavailable: ${e.javaClass.simpleName})")
        }
    }

    /** The app's own warning and error lines; apps can only read their own log. */
    private fun readLog(): String {
        // This process only: earlier crashes are already under "Last crash", and repeating them
        // here buried what's new. Warnings and errors.
        val process = Runtime.getRuntime().exec(
            arrayOf(
                "logcat", "-d", "-v", "time", "-t", "1000", "--pid=${android.os.Process.myPid()}",
                "*:W",
            ),
        )
        val lines = process.inputStream.bufferedReader().use { it.readLines() }
        process.waitFor()
        return lines.takeLast(LOG_LINES).joinToString("\n") { redact(it) }.ifEmpty { "(empty)" }
    }

    private fun keyboardEnabled(context: Context): Boolean {
        val ours = ComponentName(context, StickerKeyboardService::class.java)
        return context.getSystemService(InputMethodManager::class.java).enabledInputMethodList.any { it.component == ours }
    }

    private fun exitReason(reason: Int): String = when (reason) {
        ApplicationExitInfo.REASON_ANR -> "ANR"
        ApplicationExitInfo.REASON_CRASH -> "CRASH"
        ApplicationExitInfo.REASON_CRASH_NATIVE -> "NATIVE_CRASH"
        ApplicationExitInfo.REASON_LOW_MEMORY -> "LOW_MEMORY"
        ApplicationExitInfo.REASON_EXCESSIVE_RESOURCE_USAGE -> "EXCESSIVE_RESOURCE_USAGE"
        ApplicationExitInfo.REASON_EXIT_SELF -> "EXIT_SELF"
        ApplicationExitInfo.REASON_SIGNALED -> "SIGNALED"
        ApplicationExitInfo.REASON_USER_REQUESTED -> "USER_REQUESTED"
        ApplicationExitInfo.REASON_USER_STOPPED -> "USER_STOPPED"
        ApplicationExitInfo.REASON_DEPENDENCY_DIED -> "DEPENDENCY_DIED"
        ApplicationExitInfo.REASON_INITIALIZATION_FAILURE -> "INITIALIZATION_FAILURE"
        ApplicationExitInfo.REASON_PERMISSION_CHANGE -> "PERMISSION_CHANGE"
        ApplicationExitInfo.REASON_OTHER -> "OTHER"
        else -> "reason $reason"
    }

    private val URI = Regex("""(content|file)://\S+""")
    private val PATH = Regex("""/(storage|sdcard|data/user|data/data)/\S+""")

    /** Catches file names left over when a path contains spaces ("WhatsApp Stickers/x.webp"). */
    private val FILE = Regex("""[^\s/]+\.(webp|png|jpe?g|gif|task|tflite|spm)\b""", RegexOption.IGNORE_CASE)

    /** Removes URIs, file paths and file names, which can identify stickers. */
    fun redact(text: String): String =
        FILE.replace(PATH.replace(URI.replace(text, "<uri>"), "<path>"), "<file>")

    private fun gb(bytes: Long) = String.format(Locale.US, "%.1f GB", bytes / 1e9)

    private fun timestamp(millis: Long) = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date(millis))

    /** Keeps the last uncaught exception (redacted) so it can be included in the report. */
    object CrashLog {
        private fun file(context: Context) = File(context.noBackupFilesDir, "diagnostics/last-crash.txt")

        fun install(context: Context) {
            val appContext = context.applicationContext
            val previous = Thread.getDefaultUncaughtExceptionHandler()
            Thread.setDefaultUncaughtExceptionHandler { thread, error ->
                try {
                    val f = file(appContext)
                    f.parentFile?.mkdirs()
                    f.writeText(
                        "${timestamp(System.currentTimeMillis())} on ${thread.name}\n" +
                            redact(error.stackTraceToString()).take(MAX_CRASH_CHARS),
                    )
                } catch (ignored: Exception) {
                    // Never let crash reporting hide the original crash.
                }
                previous?.uncaughtException(thread, error)
            }
        }

        fun read(context: Context): String? = file(context).takeIf { it.isFile }?.readText()
    }
}
