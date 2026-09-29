package com.eyal98.stickerfinder.index

import android.content.ContentResolver
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import com.eyal98.stickerfinder.data.ContextUpdate
import com.eyal98.stickerfinder.data.EmbedderAccess
import com.eyal98.stickerfinder.data.ImportedChat
import com.eyal98.stickerfinder.data.StickerDao
import com.eyal98.stickerfinder.search.ChatContext
import com.eyal98.stickerfinder.search.ChatParser
import com.eyal98.stickerfinder.search.ContextLearning
import com.eyal98.stickerfinder.search.PerceptualMatch
import com.eyal98.stickerfinder.search.TextEmbedder
import com.eyal98.stickerfinder.search.Vectors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.FilterInputStream
import java.io.IOException
import java.io.InputStream
import java.security.MessageDigest
import java.util.Locale
import java.util.zip.ZipInputStream

/**
 * Learns what the user's stickers are used for from a WhatsApp chat export (a .zip with the chat's
 * .txt and the media that was sent, stickers included). Each sticker in the export is recognized
 * in the library by its perceptual hash, and the messages written just before each time it was
 * sent ([ChatContext]) are embedded and averaged into its "used for" vector ([ContextLearning]).
 *
 * Everything happens in memory, on the phone: the export is read straight from the file the user
 * picked, never extracted, and the chat text, names and numbers are dropped when this returns.
 * Only the averaged vectors, their counts and a hash of the chat text (to not count the same export
 * twice) are saved. Nothing about the chat is logged.
 */
class ChatImporter(
    private val resolver: ContentResolver,
    private val dao: StickerDao,
    private val embedders: EmbedderAccess,
) {
    enum class Step { READING, LEARNING }

    /** [done] of [total] (bytes while reading, stickers while learning); total 0 when unknown. */
    data class Progress(val step: Step, val done: Long, val total: Long)

    data class Summary(
        /** Sticker sends in the chat. */
        val sent: Int,
        /** Of those, sends of stickers found in the library. */
        val matched: Int,
        /** Library stickers that learned something. */
        val learned: Int,
        /** Matched sends with no message just before them: counted, nothing to learn. */
        val withoutContext: Int,
    ) {
        val notInLibrary: Int get() = sent - matched
    }

    sealed interface Outcome {
        data class Learned(val summary: Summary) : Outcome
        data object AlreadyImported : Outcome

        /** Not a zip, or no chat .txt in it. */
        data object NotAChat : Outcome

        /** A chat exported without media: no sticker files to recognize. */
        data object NoMedia : Outcome

        /** No embedding model to learn with. */
        data object NoModel : Outcome
    }

    private class Export(val chat: ByteArray, val files: List<String>, val stickerHashes: Map<String, Long>)

    suspend fun import(uri: Uri, onProgress: (Progress) -> Unit): Outcome = withContext(Dispatchers.IO) {
        val export = read(uri, onProgress) ?: return@withContext Outcome.NotAChat
        val hash = sha256(export.chat)
        if (dao.importedChatCount(hash) > 0) return@withContext Outcome.AlreadyImported
        if (export.stickerHashes.isEmpty()) return@withContext Outcome.NoMedia

        val text = String(export.chat, Charsets.UTF_8).removePrefix("\uFEFF")
        val sends = ChatContext.stickerSends(ChatParser.parse(text, export.files))

        val library = dao.perceptualHashes()
        val match = PerceptualMatch(LongArray(library.size) { library[it].id }, LongArray(library.size) { library[it].perceptualHash })
        val stickerOf = HashMap<String, Long?>()
        val contexts = LinkedHashMap<Long, MutableList<String>>()
        var matched = 0
        var withoutContext = 0
        for (send in sends) {
            val id = stickerOf.getOrPut(send.file) { export.stickerHashes[send.file]?.let(match::find) } ?: continue
            matched++
            val context = send.context
            if (context == null) {
                withoutContext++
            } else {
                contexts.getOrPut(id) { ArrayList() }.add(context)
            }
        }

        val updates = ArrayList<ContextUpdate>()
        var model: String? = null
        for ((i, entry) in contexts.entries.withIndex()) {
            currentCoroutineContext().ensureActive()
            onProgress(Progress(Step.LEARNING, i.toLong(), contexts.size.toLong()))
            val chunks = ContextLearning.chunks(entry.value)
            val vectors = ArrayList<Pair<FloatArray, Int>>()
            for (chunk in chunks) {
                val vector = embedders.withEmbedder { embedder ->
                    model = embedder.modelId
                    try {
                        Vectors.prepare(embedder.embed(chunk.text, TextEmbedder.Kind.DOCUMENT), embedder.dimensions)
                    } catch (e: RuntimeException) {
                        // Only the error type: its message could quote the text.
                        Log.w(TAG, "Embedding failed: ${e.javaClass.simpleName}")
                        null
                    }
                } ?: if (model == null) return@withContext Outcome.NoModel else continue
                vectors += vector to chunk.uses
            }
            ContextLearning.mean(vectors)?.let { updates += ContextUpdate(entry.key, vectors.sumOf { v -> v.second }, it) }
        }
        val usedModel = model
        // A chat nothing was learned from isn't remembered, so it can be imported again once the
        // library has its stickers (a new phone may still be reading them).
        if (usedModel != null && updates.isNotEmpty()) {
            dao.saveChatLearning(ImportedChat(hash, System.currentTimeMillis()), usedModel, updates)
        }
        Log.i(TAG, "Chat import: ${sends.size} sends, $matched matched, ${updates.size} learned")
        Outcome.Learned(Summary(sends.size, matched, updates.size, withoutContext))
    }

    /**
     * Reads the export in one pass (a zip stream can't seek): the chat text into memory, and each
     * sticker's perceptual hash. Photos, videos and the rest are skipped without being kept.
     * Returns null when it isn't a zip or holds no chat.
     */
    private suspend fun read(uri: Uri, onProgress: (Progress) -> Unit): Export? {
        val total = size(uri)
        val input = resolver.openInputStream(uri) ?: throw IOException("Cannot open the export")
        val counter = CountingStream(input.buffered(BUFFER))
        var chat: ByteArray? = null
        var chatName: String? = null
        val files = ArrayList<String>()
        val stickerHashes = HashMap<String, Long>()
        var lastReport = -1L
        ZipInputStream(counter).use { zip ->
            while (true) {
                currentCoroutineContext().ensureActive()
                val entry = zip.nextEntry ?: break
                if (entry.isDirectory) continue
                val name = entry.name.substringAfterLast('/')
                files += name
                when {
                    name.endsWith(".txt", ignoreCase = true) -> {
                        // The chat is "WhatsApp Chat with ….txt" (Android) or "_chat.txt" (iOS);
                        // any other text file only counts if there's no such one.
                        if (chat == null || !isChatName(chatName) && isChatName(name)) {
                            readLimited(zip, MAX_CHAT_BYTES)?.let {
                                chat = it
                                chatName = name
                            }
                        }
                    }
                    name.endsWith(".webp", ignoreCase = true) && stickerHashes.size < MAX_STICKERS -> {
                        readLimited(zip, MAX_STICKER_BYTES)?.let { bytes -> hashOf(bytes)?.let { stickerHashes[name] = it } }
                    }
                }
                // Every megabyte is plenty for a progress bar.
                if (counter.count - lastReport > 1_000_000) {
                    lastReport = counter.count
                    onProgress(Progress(Step.READING, counter.count, total))
                }
            }
        }
        return chat?.let { Export(it, files, stickerHashes) }
    }

    private fun isChatName(name: String?) =
        name != null && (name.startsWith("WhatsApp", ignoreCase = true) || name.equals("_chat.txt", ignoreCase = true))

    /** The export's size, if the file's provider says; 0 when unknown. */
    private fun size(uri: Uri): Long =
        try {
            resolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)?.use { c ->
                if (c.moveToFirst() && !c.isNull(0)) c.getLong(0) else 0L
            } ?: 0L
        } catch (e: RuntimeException) {
            0L
        }

    /** The current entry's bytes, or null (skipping the rest of it) if it's over [limit]. */
    private fun readLimited(zip: ZipInputStream, limit: Int): ByteArray? {
        val out = ByteArrayOutputStream()
        val buffer = ByteArray(BUFFER)
        while (true) {
            val n = zip.read(buffer)
            if (n < 0) return out.toByteArray()
            if (out.size() + n > limit) return null
            out.write(buffer, 0, n)
        }
    }

    /** Hashed exactly like the indexer does, so the same sticker gets the same hash. */
    private fun hashOf(bytes: ByteArray): Long? =
        try {
            val bitmap = StickerBitmaps.decode(bytes)
            try {
                StickerBitmaps.perceptualHash(bitmap)
            } finally {
                bitmap.recycle()
            }
        } catch (e: Exception) {
            null
        }

    private fun sha256(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(Locale.ROOT, it) }

    private class CountingStream(input: InputStream) : FilterInputStream(input) {
        var count = 0L
            private set

        override fun read(): Int = super.read().also { if (it >= 0) count++ }

        override fun read(b: ByteArray, off: Int, len: Int): Int = super.read(b, off, len).also { if (it > 0) count += it }

        override fun skip(n: Long): Long = super.skip(n).also { count += it }
    }

    private companion object {
        const val TAG = "ChatImporter"
        const val BUFFER = 64 * 1024

        /** Years of a busy chat are a few MB; more than this is held back to protect memory. */
        const val MAX_CHAT_BYTES = 48 * 1024 * 1024

        /** WhatsApp stickers are at most 500 KB (animated); bigger .webp files aren't stickers. */
        const val MAX_STICKER_BYTES = 3 * 1024 * 1024

        /** Hashes are 8 bytes each, but decoding takes time: an upper bound for huge exports. */
        const val MAX_STICKERS = 20_000
    }
}
