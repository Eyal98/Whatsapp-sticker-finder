package com.eyal98.stickerfinder.index.backup

import android.content.Context
import com.eyal98.stickerfinder.data.GoldenSetStore
import com.eyal98.stickerfinder.data.ImageTagFilter
import com.eyal98.stickerfinder.data.SearchSettings
import com.eyal98.stickerfinder.data.StickerDao
import com.eyal98.stickerfinder.data.StickerEntity
import com.eyal98.stickerfinder.data.StickerRepository
import com.eyal98.stickerfinder.data.UserTags
import com.eyal98.stickerfinder.index.EmbedWorker
import com.eyal98.stickerfinder.index.FaceData
import com.eyal98.stickerfinder.search.PeopleNames
import com.eyal98.stickerfinder.search.SearchTags
import com.eyal98.stickerfinder.search.Vectors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.util.Base64

/**
 * Backing up what the user made, and restoring it on another phone (or this one after a reset).
 *
 * Only what can't be worked out again is saved: tags, descriptions, stars, hidden picture tags,
 * how often each sticker was used, search history, the quality test's searches and the search
 * setting; with the user's say-so, the names they gave people. Printed text, picture tags, meaning
 * vectors and faces are rebuilt by the new phone. What was learned from imported chats stays out:
 * it came from other people's messages too, so it doesn't travel. Stickers are recognized by their
 * exact content (file locations differ between phones), else file name and size, else the
 * picture's fingerprint when only one picture here has it.
 *
 * A new phone reads its stickers over hours, so whatever doesn't match yet waits in app storage and
 * is applied as indexing finds more ([applyPending]); people's names wait for face grouping.
 */
object Backup {

    const val FORMAT = "peel-it-backup"
    private const val VERSION = 1
    private const val PENDING_FILE = "restore/pending.json"

    // What a restore accepts: far above any real library, low enough that a bad file can't make
    // the phone do endless work or keep megabytes of junk waiting.
    private const val MAX_STICKERS = 50_000
    private const val MAX_PICKS = 50_000
    private const val MAX_PEOPLE = 500
    private const val MAX_FOLDERS = 1_000
    private const val MAX_TEST_SEARCHES = 1_000
    private const val MAX_LIST = 100
    private const val MAX_TEXT = 200
    private const val MAX_DESCRIPTION = 2_000
    private const val MAX_FACE_CHARS = 16_384
    private const val MAX_COUNT = 1_000_000

    private val lock = Mutex()

    /** What a restore did right away, and what waits for the stickers or faces to be found. */
    data class RestoreReport(
        val stickersRestored: Int,
        val stickersWaiting: Int,
        val picksRestored: Int,
        val testSearches: Int,
        val peopleNamed: Int,
        val peopleWaiting: Int,
    )

    /** The backup's JSON (pack it with [BackupFile]). */
    suspend fun create(context: Context, dao: StickerDao, includePeople: Boolean): String = withContext(Dispatchers.IO) {
        val stickers = dao.allStickers()
        val byId = stickers.associateBy { it.id }
        // Which folders each sticker is in, by name: names survive the move, ids don't.
        val folderNames = dao.folders().associate { it.id to it.name }
        val foldersOf = dao.folderStickers().groupBy({ it.stickerId }, { folderNames[it.folderId] }).mapValues { it.value.filterNotNull() }
        val json = JSONObject()
            .put("format", FORMAT)
            .put("version", VERSION)
            .put("createdAt", System.currentTimeMillis())

        json.put(
            "stickers",
            JSONArray(
                stickers.filter { hasUserData(it) || it.id in foldersOf }.map { s ->
                    key(s)
                        .put("folders", JSONArray(foldersOf[s.id].orEmpty()))
                        .put("tags", JSONArray(UserTags.parse(s.userTags)))
                        .putOpt("description", s.userDescription)
                        .put("starred", s.starred)
                        .put("useCount", s.useCount)
                        .putOpt("lastUsedAt", s.lastUsedAt)
                        .put("hiddenPictureTags", JSONArray(ImageTagFilter.split(s.removedImageTags)))
                },
            ),
        )
        json.put(
            "picks",
            JSONArray(
                dao.allPicks().mapNotNull { p ->
                    byId[p.stickerId]?.let { s ->
                        key(s).put("query", p.queryKey).put("count", p.count).put("lastAt", p.lastAt).put("tagged", p.tagged)
                    }
                },
            ),
        )
        val golden = ByteArrayOutputStream().also { GoldenSetStore.write(GoldenSetStore(context).load(), it) }
        json.put("testSearches", JSONObject(golden.toString(Charsets.UTF_8.name())))
        json.put("settings", JSONObject().put("minSimilarity", SearchSettings(context).minSimilarity.toDouble()))
        // Every folder, empty ones too.
        json.put("folders", JSONArray(folderNames.values.toList()))
        if (includePeople) json.put("people", peopleJson(dao))
        json.toString()
    }

    /** Restores [json] (from [BackupFile.read]). @throws IOException for a file that isn't a backup. */
    suspend fun restore(context: Context, dao: StickerDao, repository: StickerRepository, json: String): RestoreReport =
        lock.withLock {
            val backup = try {
                JSONObject(json).also {
                    if (it.optString("format") != FORMAT) throw IOException("Not a Peel-It backup")
                    // From a newer app: its fields may mean something else.
                    if (it.optInt("version", -1) !in 1..VERSION) throw IOException("Unsupported backup version")
                }
            } catch (e: JSONException) {
                throw IOException("Not a Peel-It backup", e)
            }

            // Test searches and the search setting apply at once: they don't need the stickers.
            var testSearches = 0
            backup.optJSONObject("testSearches")?.let { saved ->
                val store = GoldenSetStore(context)
                val existing = store.load()
                val incoming = try {
                    GoldenSetStore.read(ByteArrayInputStream(saved.toString().toByteArray(Charsets.UTF_8)))
                } catch (e: JSONException) {
                    emptyList()
                }
                val added = incoming.filter { q -> existing.none { it.id == q.id } }.take((MAX_TEST_SEARCHES - existing.size).coerceAtLeast(0))
                if (added.isNotEmpty()) store.save(existing + added)
                testSearches = added.size
            }
            backup.optJSONObject("settings")?.optDouble("minSimilarity")?.takeIf { it in 0.0..1.0 }?.let {
                SearchSettings(context).minSimilarity = it.toFloat()
            }

            // Folders by name, so even empty ones come back.
            val existingFolders = dao.folders().map { it.name.lowercase() }.toSet()
            backup.optJSONArray("folders").strings().filter { it.length <= MAX_TEXT && it.lowercase() !in existingFolders }
                .distinctBy { it.lowercase() }.take((MAX_FOLDERS - existingFolders.size).coerceAtLeast(0))
                .forEach { repository.createFolder(it) }

            // Stickers and search history: added to whatever already waits from an earlier restore.
            val pending = readPending(context)
            // The same backup restored twice doesn't wait twice.
            // Only fields we know, in sensible ranges: a hand-edited or damaged file can't smuggle in
            // more (or bigger) than a real backup holds.
            val stickers = (pending.stickers + backup.optJSONArray("stickers").objects().mapNotNull(::cleanSticker))
                .distinctBy { it.toString() }.take(MAX_STICKERS)
            val picks = (pending.picks + backup.optJSONArray("picks").objects().mapNotNull(::cleanPick))
                .distinctBy { it.toString() }.take(MAX_PICKS)
            val people = (pending.people + backup.optJSONArray("people").objects().mapNotNull(::cleanPerson))
                .distinctBy { it.optString("name") }.take(MAX_PEOPLE)
            val applied = apply(dao, repository, stickers, picks)
            writePending(context, Pending(applied.waitingStickers, applied.waitingPicks, people))
            val named = namePeople(context, dao)
            if (applied.stickers > 0 || named > 0) EmbedWorker.runForEdit(context)
            RestoreReport(
                stickersRestored = applied.stickers,
                stickersWaiting = applied.waitingStickers.size,
                picksRestored = applied.picks,
                testSearches = testSearches,
                peopleNamed = named,
                peopleWaiting = readPending(context).people.size,
            )
        }

    /** Applies what waits from a restore to stickers found since. Returns how many were restored. */
    suspend fun applyPending(context: Context, dao: StickerDao, repository: StickerRepository): Int = lock.withLock {
        val pending = readPending(context)
        if (pending.stickers.isEmpty() && pending.picks.isEmpty()) return@withLock 0
        val applied = apply(dao, repository, pending.stickers, pending.picks)
        writePending(context, pending.copy(stickers = applied.waitingStickers, picks = applied.waitingPicks))
        if (applied.stickers > 0) EmbedWorker.runForEdit(context)
        applied.stickers
    }

    /**
     * Names the face groups that match people saved in a restored backup; each saved person is
     * used once. Runs after face grouping. Returns how many groups got a name.
     */
    suspend fun applyPeople(context: Context, dao: StickerDao): Int = lock.withLock { namePeople(context, dao) }

    /** [applyPeople], with [lock] held: the waiting items are read and written under it. */
    private suspend fun namePeople(context: Context, dao: StickerDao): Int {
        if (readPending(context).people.isEmpty()) return 0
        val named = FaceData.writeIfEnabled(context) {
            val pending = readPending(context)
            nameFrom(dao, pending) { used ->
                writePending(context, pending.copy(people = pending.people.filter { it.optString("name") !in used }))
            }
        } ?: 0
        return named
    }

    /** Names unnamed face groups after [pending]'s people; [done] gets the names used. */
    private suspend fun nameFrom(dao: StickerDao, pending: Pending, done: (Set<String>) -> Unit): Int {
        val saved = pending.people.mapNotNull { p ->
            val name = p.optString("name").takeIf { it.isNotBlank() } ?: return@mapNotNull null
            val centroid = p.optString("face").takeIf { it.isNotEmpty() }?.let {
                try {
                    Vectors.decode(Base64.getDecoder().decode(it))
                } catch (e: IllegalArgumentException) {
                    null
                }
            }?.takeIf { it.isNotEmpty() && it.all(Float::isFinite) }
            centroid?.let { name to it }
        }
        val people = dao.people()
        val unnamed = people.filter { it.name == null }.map { it.id }.toSet()
        val taken = people.mapNotNull { it.name }.toSet()
        val groups = dao.faceRows().filter { it.personId in unnamed }.groupBy { it.personId!! }
            .mapNotNull { (id, faces) -> PeopleNames.centroid(faces.map { Vectors.decode(it.vector) })?.let { id to it } }
            .toMap()
        // A saved face from another face model (other length) can't be compared: it's left waiting.
        val dims = groups.values.firstOrNull()?.size
        val matches = PeopleNames.match(groups, saved.filter { it.first !in taken && it.second.size == dims })
        // One group per saved person: the closest one, if a person was split into two groups.
        val chosen = matches.entries.groupBy { it.value }.map { (_, entries) ->
            entries.maxBy { e -> Vectors.dot(groups.getValue(e.key), saved.first { it.first == e.value }.second) }
        }
        for ((group, name) in chosen) dao.renamePerson(group, name)
        if (chosen.isNotEmpty()) dao.syncPeopleNames()
        done(chosen.map { it.value }.toSet())
        return chosen.size
    }

    /** Forgets people's saved faces waiting from a restore (with "Delete all face data"). */
    suspend fun forgetPeople(context: Context): Unit = lock.withLock {
        val pending = readPending(context)
        if (pending.people.isNotEmpty()) writePending(context, pending.copy(people = emptyList()))
    }

    // --- Applying ---------------------------------------------------------------------------

    private class Applied(val stickers: Int, val picks: Int, val waitingStickers: List<JSONObject>, val waitingPicks: List<JSONObject>)

    private suspend fun apply(
        dao: StickerDao,
        repository: StickerRepository,
        stickers: List<JSONObject>,
        picks: List<JSONObject>,
    ): Applied {
        val local = dao.allStickers()
        val byContent = local.filter { !it.contentHash.isNullOrEmpty() }.groupBy { it.contentHash!! }
        val byHash = local.filter { it.perceptualHash != null }.groupBy { it.perceptualHash!! }
        val byFile = local.groupBy { it.displayName to it.sizeBytes }

        /**
         * The local files that are this backed-up sticker: the same bytes, else the same file
         * name and size, else the same perceptual hash only when that's one picture here.
         * Different pictures can share a perceptual hash (flat colour variants, animations with
         * the same first frame); restoring tags onto the wrong one would be worse than waiting.
         */
        fun find(entry: JSONObject): List<StickerEntity> {
            entry.optString("sha").takeIf { it.isNotEmpty() }?.let { sha -> byContent[sha]?.let { return it } }
            byFile[entry.optString("name") to entry.optLong("size", -1)]?.let { return it }
            val hash = entry.optString("hash").takeIf { it.isNotEmpty() }?.toULongOrNull(16)?.toLong() ?: return emptyList()
            val candidates = byHash[hash].orEmpty()
            val contents = candidates.map { it.contentHash }.toSet()
            // One picture: a single file, or copies known to have the same bytes.
            return if (candidates.size == 1 || (contents.size == 1 && !contents.first().isNullOrEmpty())) candidates else emptyList()
        }

        // Folders by name (ignoring case), made on first use.
        val folderIds = dao.folders().associate { it.name.lowercase() to it.id }.toMutableMap()
        suspend fun folderId(name: String): Long =
            folderIds.getOrPut(name.lowercase()) { repository.createFolder(name) }

        var restored = 0
        val waitingStickers = mutableListOf<JSONObject>()
        for (entry in stickers) {
            val matches = find(entry)
            if (matches.isEmpty()) {
                waitingStickers += entry
                continue
            }
            val ids = matches.map { it.id }
            val tags = entry.optJSONArray("tags").strings()
            if (tags.isNotEmpty()) repository.addTags(ids, tags)
            val hidden = entry.optJSONArray("hiddenPictureTags").strings()
            if (hidden.isNotEmpty()) repository.editHiddenImageTags(ids, hide = hidden, show = emptyList())
            entry.optString("description").takeIf { it.isNotBlank() }?.let { description ->
                // Never overwrite a description written on this phone.
                val empty = matches.filter { it.userDescription.isNullOrBlank() }.map { it.id }
                if (empty.isNotEmpty()) repository.setDescription(empty, description)
            }
            if (entry.optBoolean("starred")) ids.forEach { repository.setStarred(it, true) }
            for (folder in entry.optJSONArray("folders").strings()) repository.addToFolder(folderId(folder), ids)
            val useCount = entry.optInt("useCount")
            val lastUsed = if (entry.has("lastUsedAt")) entry.optLong("lastUsedAt") else null
            if (useCount > 0 || lastUsed != null) ids.forEach { dao.mergeUse(it, useCount, lastUsed) }
            restored++
        }

        var restoredPicks = 0
        val waitingPicks = mutableListOf<JSONObject>()
        for (entry in picks) {
            val matches = find(entry)
            val query = entry.optString("query")
            if (query.isEmpty()) continue
            if (matches.isEmpty()) {
                waitingPicks += entry
                continue
            }
            // One pick per image is enough: search shows copies of one image once.
            // Whether the search was already made a tag (or found not to fit one), so a tag the user
            // removed doesn't come back. Older backups didn't say: a pick sent often enough had.
            val count = entry.optInt("count", 1)
            val tagged = if (entry.has("tagged")) entry.optBoolean("tagged") else count >= SearchTags.PICKS
            dao.mergePick(query, matches.first().id, count, entry.optLong("lastAt"), tagged)
            restoredPicks++
        }
        return Applied(restored, restoredPicks, waitingStickers, waitingPicks)
    }

    // --- Checking what a file holds -----------------------------------------------------------

    private val HEX = Regex("[0-9a-f]+")

    /** The key fields of [entry], checked; null when it has no usable key. */
    private fun cleanKey(entry: JSONObject): JSONObject? {
        val out = JSONObject()
        entry.optString("sha").takeIf { it.length == 64 && HEX.matches(it) }?.let { out.put("sha", it) }
        entry.optString("hash").takeIf { it.length in 1..16 && HEX.matches(it) }?.let { out.put("hash", it) }
        entry.optString("name").takeIf { it.isNotEmpty() && it.length <= MAX_TEXT }?.let { name ->
            val size = entry.optLong("size", -1)
            if (size >= 0) out.put("name", name).put("size", size)
        }
        return out.takeIf { it.length() > 0 }
    }

    private fun texts(entry: JSONObject, key: String, maxLength: Int = MAX_TEXT) =
        JSONArray(entry.optJSONArray(key).strings().filter { it.length <= maxLength }.distinct().take(MAX_LIST))

    private fun cleanSticker(entry: JSONObject): JSONObject? {
        val out = cleanKey(entry) ?: return null
        out.put("folders", texts(entry, "folders"))
        out.put("tags", texts(entry, "tags"))
        out.put("hiddenPictureTags", texts(entry, "hiddenPictureTags"))
        entry.optString("description").takeIf { it.isNotBlank() }?.let { out.put("description", it.take(MAX_DESCRIPTION)) }
        out.put("starred", entry.optBoolean("starred"))
        out.put("useCount", entry.optInt("useCount").coerceIn(0, MAX_COUNT))
        if (entry.has("lastUsedAt")) entry.optLong("lastUsedAt", -1).takeIf { it >= 0 }?.let { out.put("lastUsedAt", it) }
        return out
    }

    private fun cleanPick(entry: JSONObject): JSONObject? {
        val out = cleanKey(entry) ?: return null
        val query = entry.optString("query").takeIf { it.isNotBlank() && it.length <= MAX_TEXT } ?: return null
        out.put("query", query)
        out.put("count", entry.optInt("count", 1).coerceIn(1, MAX_COUNT))
        out.put("lastAt", entry.optLong("lastAt").coerceAtLeast(0))
        if (entry.has("tagged")) out.put("tagged", entry.optBoolean("tagged"))
        return out
    }

    private fun cleanPerson(entry: JSONObject): JSONObject? {
        val name = entry.optString("name").takeIf { it.isNotBlank() && it.length <= MAX_TEXT } ?: return null
        val face = entry.optString("face").takeIf { it.isNotEmpty() && it.length <= MAX_FACE_CHARS } ?: return null
        return JSONObject().put("name", name).put("face", face)
    }

    // --- Format -----------------------------------------------------------------------------

    private fun hasUserData(s: StickerEntity) =
        s.userTags.isNotBlank() || !s.userDescription.isNullOrBlank() || s.starred || s.useCount > 0 ||
            !s.removedImageTags.isNullOrBlank()

    /** How a sticker is found again: its exact content, else its file name and size, else its picture's fingerprint. */
    private fun key(s: StickerEntity): JSONObject = JSONObject()
        // As hex text: JSON numbers can't hold 64 bits exactly.
        .putOpt("hash", s.perceptualHash?.toULong()?.toString(16))
        .putOpt("sha", s.contentHash?.takeIf { it.isNotEmpty() })
        .put("name", s.displayName)
        .put("size", s.sizeBytes)

    /** For each named person, the average of their face vectors (never the faces' pictures). */
    private suspend fun peopleJson(dao: StickerDao): JSONArray {
        val names = dao.people().filter { it.name != null }.associate { it.id to it.name!! }
        val faces = dao.faceRows().filter { it.personId in names }.groupBy { it.personId!! }
        return JSONArray(
            names.mapNotNull { (id, name) ->
                val centroid = PeopleNames.centroid(faces[id].orEmpty().map { Vectors.decode(it.vector) }) ?: return@mapNotNull null
                JSONObject().put("name", name).put("face", Base64.getEncoder().encodeToString(Vectors.encode(centroid)))
            },
        )
    }

    private data class Pending(val stickers: List<JSONObject>, val picks: List<JSONObject>, val people: List<JSONObject>)

    private fun pendingFile(context: Context) = File(context.noBackupFilesDir, PENDING_FILE)

    private fun readPending(context: Context): Pending {
        val file = pendingFile(context)
        if (!file.isFile) return Pending(emptyList(), emptyList(), emptyList())
        return try {
            val json = JSONObject(file.readText())
            Pending(json.optJSONArray("stickers").objects(), json.optJSONArray("picks").objects(), json.optJSONArray("people").objects())
        } catch (e: JSONException) {
            Pending(emptyList(), emptyList(), emptyList())
        } catch (e: IOException) {
            Pending(emptyList(), emptyList(), emptyList())
        }
    }

    private fun writePending(context: Context, pending: Pending) {
        val file = pendingFile(context)
        if (pending.stickers.isEmpty() && pending.picks.isEmpty() && pending.people.isEmpty()) {
            file.delete()
            return
        }
        file.parentFile?.mkdirs()
        val json = JSONObject()
            .put("stickers", JSONArray(pending.stickers))
            .put("picks", JSONArray(pending.picks))
            .put("people", JSONArray(pending.people))
        val tmp = File(file.path + ".tmp")
        tmp.writeText(json.toString())
        if (!tmp.renameTo(file)) throw IOException("Could not save the restore's waiting items")
    }

    /** What a restore left waiting, for the About screen. */
    fun waiting(context: Context): Pair<Int, Int> = readPending(context).let { it.stickers.size to it.people.size }

    private fun JSONArray?.objects(): List<JSONObject> =
        if (this == null) emptyList() else (0 until length()).mapNotNull { optJSONObject(it) }

    private fun JSONArray?.strings(): List<String> =
        if (this == null) emptyList() else (0 until length()).mapNotNull { optString(it).takeIf { s -> s.isNotBlank() } }
}
