package com.eyal98.stickerfinder.ml

import android.content.Context
import android.util.Log
import androidx.core.content.edit
import java.io.File
import java.io.IOException

/**
 * The Granite embedding model, bundled in the app (see core/embed/granite.properties) so meaning
 * search works without importing a file. LiteRT-LM opens a model by path, and a compressed APK
 * entry has none, so the asset is copied once into app storage (excluded from backups). The copy
 * is covered by the APK signature, and it's only redone when a new app version bundles a
 * different file.
 */
object BundledEmbedding {

    private const val TAG = "BundledEmbedding"

    /** Keep in step with FetchGranite in core/embed/build.gradle.kts. */
    private const val ASSET_DIR = "embedding"
    private val SPEC = ModelCatalog.GRANITE_EMBEDDING
    private val ASSET = "$ASSET_DIR/${SPEC.fileName}"

    /** Space to leave free on top of the copy. */
    private const val FREE_SPACE_MARGIN = 300_000_000L

    private const val KEY_SHA = "sha256"

    enum class Status { NOT_BUNDLED, READY, NOT_ENOUGH_SPACE, FAILED }

    private fun dir(context: Context) = File(context.noBackupFilesDir, "models/bundled")
    private fun file(context: Context) = File(dir(context), SPEC.fileName)
    private fun prefs(context: Context) = context.getSharedPreferences("bundled_embedding", Context.MODE_PRIVATE)

    fun isBundled(context: Context): Boolean =
        context.assets.list(ASSET_DIR)?.contains(SPEC.fileName) == true

    /** The copied model, once the copy is complete. */
    fun installed(context: Context): InstalledModel? {
        val sha = prefs(context).getString(KEY_SHA, null) ?: return null
        if (sha != SPEC.sha256) return null
        val file = file(context).takeIf { it.isFile } ?: return null
        return InstalledModel(file, SPEC, sha)
    }

    /**
     * The model meaning search uses: one the user imported (their explicit choice) or else the
     * bundled one.
     */
    fun active(context: Context): InstalledModel? = ModelStore.EMBEDDING.installed(context) ?: installed(context)

    /**
     * Copies the bundled model into app storage if it isn't there yet (a few seconds). Returns
     * [Status.READY] also when it was already installed. Call from a background thread.
     */
    @Synchronized
    fun install(context: Context): Status {
        if (!isBundled(context)) return Status.NOT_BUNDLED
        if (installed(context) != null) return Status.READY.also { removeDuplicateImport(context) }

        val dir = dir(context)
        dir.deleteRecursively()
        dir.mkdirs()
        val size = try {
            context.assets.openFd(ASSET).use { it.length }
        } catch (e: IOException) {
            -1L // Stored compressed: no descriptor, so no size up front.
        }
        val needed = (if (size > 0) size else 340_000_000L) + FREE_SPACE_MARGIN
        if (dir.usableSpace < needed) return Status.NOT_ENOUGH_SPACE

        val target = file(context)
        val part = File(dir, "${SPEC.fileName}.part")
        return try {
            context.assets.open(ASSET).use { input -> part.outputStream().use { input.copyTo(it, 1 shl 20) } }
            if (!part.renameTo(target)) throw IOException("Could not move the model into place")
            prefs(context).edit { putString(KEY_SHA, SPEC.sha256) }
            removeDuplicateImport(context)
            Status.READY
        } catch (e: IOException) {
            Log.w(TAG, "Could not install the bundled embedding model", e)
            dir.deleteRecursively()
            Status.FAILED
        }
    }

    /** An imported copy of the very same file is 330 MB for nothing: remove it. */
    private fun removeDuplicateImport(context: Context) {
        val imported = ModelStore.EMBEDDING.installed(context) ?: return
        if (imported.sha256 == SPEC.sha256) ModelStore.EMBEDDING.remove(context)
    }
}
