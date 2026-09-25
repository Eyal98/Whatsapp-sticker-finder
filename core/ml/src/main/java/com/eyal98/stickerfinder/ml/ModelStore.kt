package com.eyal98.stickerfinder.ml

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.core.content.edit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.security.MessageDigest

/** An installed model file. */
data class InstalledModel(val file: File, val model: ModelSpec?, val sha256: String) {
    /** Catalog id, or a hash-based id for a model the catalog doesn't know by name. */
    val id: String get() = model?.id ?: "custom-${sha256.take(12)}"
    val displayName: String get() = model?.displayName ?: file.name
}

/** A copied file waiting for the user to confirm its hash. */
data class PendingModel(val sha256: String, val originalName: String, val guessedModel: ModelSpec?)

/**
 * One installable model file ("slot"). Imports a file the user picked into app-private storage
 * (excluded from backups), hashing it on the way. A file whose hash is pinned in [catalog] is
 * installed straight away; any other file waits in [pending] until the user confirms its hash.
 */
class ModelStore private constructor(
    private val slot: String,
    val catalog: List<ModelSpec>,
    private val fileName: String,
    /** File extensions this slot accepts; the wrong file can crash the native runtime. */
    val extensions: Set<String>,
    /** The [ModelCrashGuard] feature this file belongs to. */
    val feature: String,
) {

    companion object {
        private const val KEY_SHA = "sha256"
        private const val KEY_MODEL_ID = "model_id"
        private const val KEY_PENDING_SHA = "pending_sha256"
        private const val KEY_PENDING_NAME = "pending_name"

        /** Space to leave free on top of the model itself. */
        private const val FREE_SPACE_MARGIN = 500_000_000L

        val CAPTION = ModelStore(
            "caption", ModelCatalog.CAPTION_MODELS, "caption.task", setOf("task"), ModelCrashGuard.CAPTION,
        )
        val EMBEDDING = ModelStore(
            "embedding", ModelCatalog.EMBEDDING_MODELS, "embedding.tflite", setOf("tflite"), ModelCrashGuard.EMBEDDING,
        )
        val EMBEDDING_TOKENIZER = ModelStore(
            "embedding_tokenizer", ModelCatalog.TOKENIZERS, "embedding.spm", setOf("model", "spm"), ModelCrashGuard.EMBEDDING,
        )
    }

    sealed interface ImportResult {
        data class Installed(val model: InstalledModel) : ImportResult
        data class NeedsConfirmation(val pending: PendingModel) : ImportResult
        data class NotEnoughSpace(val neededBytes: Long) : ImportResult
        data class WrongFileType(val expected: Set<String>) : ImportResult
        data object Failed : ImportResult
    }

    private fun dir(context: Context) = File(context.noBackupFilesDir, "models").apply { mkdirs() }
    private fun modelFile(context: Context) = File(dir(context), fileName)
    private fun pendingFile(context: Context) = File(dir(context), "$slot.pending")
    private fun prefs(context: Context) = context.getSharedPreferences("${slot}_model", Context.MODE_PRIVATE)

    private fun byId(id: String) = catalog.firstOrNull { it.id == id }
    private fun byHash(sha256: String) = catalog.firstOrNull { it.sha256 == sha256 }
    private fun byFileName(name: String) = catalog.firstOrNull { it.fileName.equals(name, ignoreCase = true) }

    fun installed(context: Context): InstalledModel? {
        val file = modelFile(context)
        val sha = prefs(context).getString(KEY_SHA, null) ?: return null
        if (!file.isFile) return null
        val model = prefs(context).getString(KEY_MODEL_ID, null)?.let(::byId)
        return InstalledModel(file, model, sha)
    }

    fun pending(context: Context): PendingModel? {
        val sha = prefs(context).getString(KEY_PENDING_SHA, null) ?: return null
        if (!pendingFile(context).isFile) return null
        val name = prefs(context).getString(KEY_PENDING_NAME, null).orEmpty()
        return PendingModel(sha, name, byFileName(name))
    }

    /**
     * Copies [source] into app storage. Reports progress from 0 to 1. Long-running: call from a
     * background coroutine; cancelling it deletes the partial copy.
     */
    suspend fun import(context: Context, source: Uri, onProgress: (Float) -> Unit): ImportResult =
        withContext(Dispatchers.IO) {
            val resolver = context.contentResolver
            val (name, size) = resolver.query(source, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)
                ?.use { c -> if (c.moveToFirst()) (c.getString(0).orEmpty() to c.getLong(1)) else null }
                ?: ("" to -1L)
            // Checked by name, before copying gigabytes: some providers don't report a name, and
            // then the file is accepted.
            val extension = name.substringAfterLast('.', "").lowercase()
            if (name.isNotEmpty() && extension !in extensions) {
                return@withContext ImportResult.WrongFileType(extensions)
            }
            if (size > 0 && dir(context).usableSpace < size + FREE_SPACE_MARGIN) {
                return@withContext ImportResult.NotEnoughSpace(size + FREE_SPACE_MARGIN)
            }

            discardPending(context)
            val part = pendingFile(context)
            val digest = MessageDigest.getInstance("SHA-256")
            try {
                val input = resolver.openInputStream(source) ?: throw IOException("Cannot open $source")
                input.use { inStream ->
                    part.outputStream().use { out ->
                        val buffer = ByteArray(1 shl 20)
                        var copied = 0L
                        while (true) {
                            ensureActive()
                            val n = inStream.read(buffer)
                            if (n < 0) break
                            out.write(buffer, 0, n)
                            digest.update(buffer, 0, n)
                            copied += n
                            if (size > 0) onProgress((copied.toFloat() / size).coerceAtMost(1f))
                        }
                    }
                }
            } catch (e: IOException) {
                part.delete()
                return@withContext ImportResult.Failed
            } catch (e: SecurityException) {
                part.delete()
                return@withContext ImportResult.Failed
            } finally {
                // Cancellation also lands here; don't leave a partial copy behind.
                if (!isActive) part.delete()
            }

            val sha = digest.digest().joinToString("") { "%02x".format(it) }
            prefs(context).edit {
                putString(KEY_PENDING_SHA, sha)
                putString(KEY_PENDING_NAME, name)
            }
            val pinned = byHash(sha)
            if (pinned != null) {
                ImportResult.Installed(confirmPending(context, pinned) ?: return@withContext ImportResult.Failed)
            } else {
                ImportResult.NeedsConfirmation(PendingModel(sha, name, byFileName(name)))
            }
        }

    /** Installs the pending file, replacing any current model. */
    fun confirmPending(context: Context, model: ModelSpec? = pending(context)?.guessedModel): InstalledModel? {
        val sha = prefs(context).getString(KEY_PENDING_SHA, null) ?: return null
        val target = modelFile(context)
        target.delete()
        if (!pendingFile(context).renameTo(target)) return null
        prefs(context).edit {
            putString(KEY_SHA, sha)
            if (model != null) putString(KEY_MODEL_ID, model.id) else remove(KEY_MODEL_ID)
            remove(KEY_PENDING_SHA)
            remove(KEY_PENDING_NAME)
        }
        ModelCrashGuard.enable(context, feature)
        return installed(context)
    }

    fun discardPending(context: Context) {
        pendingFile(context).delete()
        prefs(context).edit {
            remove(KEY_PENDING_SHA)
            remove(KEY_PENDING_NAME)
        }
    }

    fun remove(context: Context) {
        modelFile(context).delete()
        prefs(context).edit {
            remove(KEY_SHA)
            remove(KEY_MODEL_ID)
        }
        ModelCrashGuard.enable(context, feature)
    }
}
