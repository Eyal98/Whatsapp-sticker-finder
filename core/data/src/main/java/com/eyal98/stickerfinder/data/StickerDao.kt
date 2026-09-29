package com.eyal98.stickerfinder.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.eyal98.stickerfinder.search.ContextLearning
import com.eyal98.stickerfinder.search.IndexTerms
import com.eyal98.stickerfinder.search.Vectors
import kotlinx.coroutines.flow.Flow

@Dao
abstract class StickerDao {

    @Query("SELECT id, documentUri, sizeBytes, lastModified FROM stickers")
    abstract suspend fun fileStates(): List<StickerFileState>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    abstract suspend fun insertAll(stickers: List<StickerEntity>)

    /** Records a changed file and marks it for re-indexing, picture tags and faces. */
    @Query(
        "UPDATE stickers SET sizeBytes = :sizeBytes, lastModified = :lastModified, " +
            "indexedAt = NULL, captionedAt = NULL, imageTaggedAt = NULL, facesScannedAt = NULL, " +
            "indexAttempts = 0, captionAttempts = 0, imageTagAttempts = 0, faceScanAttempts = 0 WHERE id = :id",
    )
    abstract suspend fun markChanged(id: Long, sizeBytes: Long, lastModified: Long)

    @Query("DELETE FROM stickers WHERE id IN (:ids)")
    abstract suspend fun deleteStickers(ids: List<Long>)

    @Query("DELETE FROM sticker_fts WHERE rowid IN (:ids)")
    abstract suspend fun deleteFts(ids: List<Long>)

    @Query("DELETE FROM sticker_vectors WHERE stickerId IN (:ids)")
    abstract suspend fun deleteVectors(ids: List<Long>)

    @Query("DELETE FROM sticker_image_vectors WHERE stickerId IN (:ids)")
    abstract suspend fun deleteImageVectors(ids: List<Long>)

    @Query("DELETE FROM sticker_faces WHERE stickerId IN (:ids)")
    abstract suspend fun deleteFacesOf(ids: List<Long>)

    @Query("DELETE FROM sticker_contexts WHERE stickerId IN (:ids)")
    abstract suspend fun deleteContexts(ids: List<Long>)

    @Query("DELETE FROM folder_stickers WHERE stickerId IN (:ids)")
    abstract suspend fun deleteFromFolders(ids: List<Long>)

    /** Deletes stickers together with their search index rows, vectors and faces. */
    @Transaction
    open suspend fun deleteWithFts(ids: List<Long>) {
        // SQLite limits bound parameters per statement, so delete in chunks.
        ids.chunked(500).forEach {
            deleteFts(it)
            deleteVectors(it)
            deleteImageVectors(it)
            deleteFacesOf(it)
            deleteContexts(it)
            deleteFromFolders(it)
            deleteStickers(it)
        }
        deleteEmptyPeople()
    }

    @Query("SELECT * FROM stickers")
    abstract suspend fun allStickers(): List<StickerEntity>

    @Query("SELECT * FROM stickers WHERE id IN (:ids)")
    abstract suspend fun byIds(ids: List<Long>): List<StickerEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun upsertVector(vector: StickerVector)

    @Query("SELECT stickerId, facet, model, fingerprint FROM sticker_vectors")
    abstract suspend fun vectorStates(): List<StickerVectorState>

    @Query("DELETE FROM sticker_vectors WHERE stickerId = :stickerId AND facet = :facet")
    abstract suspend fun deleteVector(stickerId: Long, facet: Int)

    /** Meaning vectors with their sticker's pack, in (sticker, facet) order, a page at a time. */
    @Query(
        "SELECT v.stickerId, v.facet, v.vector, s.packName FROM sticker_vectors v JOIN stickers s ON s.id = v.stickerId " +
            "WHERE v.model = :model AND (v.stickerId > :afterId OR (v.stickerId = :afterId AND v.facet > :afterFacet)) " +
            "ORDER BY v.stickerId, v.facet LIMIT :limit",
    )
    abstract suspend fun meaningIndexPage(model: String, afterId: Long, afterFacet: Int, limit: Int): List<MeaningIndexRow>

    @Query("SELECT COUNT(*) AS count, TOTAL(fingerprint) + TOTAL(stickerId) AS total FROM sticker_vectors")
    abstract suspend fun vectorSignature(): VectorSignature

    /** Changes whenever a chat import adds to a context vector, or they're forgotten. */
    @Query("SELECT COUNT(*) AS count, TOTAL(uses) * 1000003 + TOTAL(stickerId) AS total FROM sticker_contexts")
    abstract suspend fun contextSignature(): VectorSignature

    /** Context vectors learned from chats, with their sticker's pack, in id order, a page at a time. */
    @Query(
        "SELECT c.stickerId, c.vector, s.packName FROM sticker_contexts c JOIN stickers s ON s.id = c.stickerId " +
            "WHERE c.model = :model AND c.stickerId > :afterId ORDER BY c.stickerId LIMIT :limit",
    )
    abstract suspend fun contextIndexPage(model: String, afterId: Long, limit: Int): List<ContextIndexRow>

    /** Every decoded sticker's perceptual hash, to recognize stickers in a chat export. */
    @Query("SELECT id, perceptualHash FROM stickers WHERE perceptualHash IS NOT NULL")
    abstract suspend fun perceptualHashes(): List<StickerHashRow>

    @Query("SELECT * FROM sticker_contexts WHERE stickerId = :id")
    abstract suspend fun stickerContext(id: Long): StickerContext?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun upsertContext(context: StickerContext)

    @Query("SELECT COUNT(*) FROM imported_chats WHERE hash = :hash")
    abstract suspend fun importedChatCount(hash: String): Int

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    abstract suspend fun insertImportedChat(chat: ImportedChat)

    /**
     * Folds one chat's lessons into each sticker's running average (see [ContextLearning.fold])
     * and remembers the chat, in one transaction so a chat never counts half or twice. A vector
     * from another model is replaced rather than averaged with.
     */
    @Transaction
    open suspend fun saveChatLearning(chat: ImportedChat, model: String, updates: List<ContextUpdate>) {
        if (importedChatCount(chat.hash) > 0) return
        for (u in updates) {
            val old = stickerContext(u.stickerId)?.takeIf { it.model == model }
            val oldUses = old?.uses ?: 0
            val mean = ContextLearning.fold(old?.let { Vectors.decode(it.vector) }, oldUses, u.mean, u.uses)
            upsertContext(StickerContext(u.stickerId, model, oldUses + u.uses, Vectors.encode(mean)))
        }
        insertImportedChat(chat)
    }

    @Query("DELETE FROM sticker_contexts")
    abstract suspend fun deleteAllContexts()

    @Query("DELETE FROM imported_chats")
    abstract suspend fun deleteImportedChats()

    /** Deletes everything learned from chats, and which chats those were. */
    @Transaction
    open suspend fun forgetChats() {
        deleteAllContexts()
        deleteImportedChats()
    }

    @Query("SELECT COUNT(*) FROM imported_chats")
    abstract fun observeImportedChatCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM sticker_contexts")
    abstract fun observeContextCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM imported_chats")
    abstract suspend fun importedChatTotal(): Int

    @Query("SELECT COUNT(*) FROM sticker_contexts")
    abstract suspend fun contextCount(): Int

    /** Stickers with at least one meaning vector. */
    @Query("SELECT COUNT(DISTINCT stickerId) FROM sticker_vectors")
    abstract fun observeVectorCount(): Flow<Int>

    /** Counts only, for the diagnostics report; no sticker content. */
    @Query(
        "SELECT COUNT(*) AS total, " +
            "COALESCE(SUM(CASE WHEN indexedAt IS NOT NULL AND indexVersion >= :version THEN 1 ELSE 0 END), 0) AS indexedCount, " +
            "COALESCE(SUM(CASE WHEN indexAttempts > 0 AND (indexedAt IS NULL OR indexVersion < :version) THEN 1 ELSE 0 END), 0) AS failingNow, " +
            "COALESCE(SUM(CASE WHEN indexAttempts > 1 THEN 1 ELSE 0 END), 0) AS retried, " +
            "COALESCE(MAX(indexAttempts), 0) AS maxAttempts, " +
            "COALESCE(SUM(CASE WHEN perceptualHash IS NULL AND indexedAt IS NOT NULL THEN 1 ELSE 0 END), 0) AS undecodable, " +
            "COALESCE(SUM(CASE WHEN ocrText IS NOT NULL AND ocrText != '' THEN 1 ELSE 0 END), 0) AS withText, " +
            "COALESCE(SUM(CASE WHEN captionedAt IS NOT NULL THEN 1 ELSE 0 END), 0) AS captioned, " +
            "COALESCE(SUM(CASE WHEN captionEn IS NOT NULL OR captionHe IS NOT NULL THEN 1 ELSE 0 END), 0) AS withCaption, " +
            "COALESCE(SUM(CASE WHEN captionAttempts > 1 THEN 1 ELSE 0 END), 0) AS captionRetried, " +
            "COALESCE(SUM(CASE WHEN isAnimated THEN 1 ELSE 0 END), 0) AS animated, " +
            "COALESCE(SUM(CASE WHEN starred THEN 1 ELSE 0 END), 0) AS starred, " +
            "COALESCE(SUM(CASE WHEN imageTaggedAt IS NOT NULL THEN 1 ELSE 0 END), 0) AS imageTagged, " +
            "COALESCE(SUM(CASE WHEN imageTags IS NOT NULL AND imageTags != '' THEN 1 ELSE 0 END), 0) AS withImageTags, " +
            "COALESCE(SUM(CASE WHEN imageTagAttempts > 1 THEN 1 ELSE 0 END), 0) AS imageTagRetried, " +
            "COALESCE(SUM(CASE WHEN packName IS NOT NULL THEN 1 ELSE 0 END), 0) AS withPackName, " +
            "COALESCE(SUM(CASE WHEN emojiWords IS NOT NULL AND emojiWords != '' THEN 1 ELSE 0 END), 0) AS withEmojis, " +
            "COALESCE(SUM(CASE WHEN facesScannedAt IS NOT NULL THEN 1 ELSE 0 END), 0) AS faceScanned, " +
            "(SELECT COUNT(*) FROM sticker_faces) AS faces, " +
            "(SELECT COUNT(*) FROM sticker_faces WHERE personId IS NOT NULL) AS groupedFaces, " +
            "(SELECT COUNT(*) FROM people) AS people, " +
            "(SELECT COUNT(*) FROM people WHERE name IS NOT NULL) AS namedPeople, " +
            "(SELECT COUNT(DISTINCT stickerId) FROM sticker_vectors) AS vectors " +
            "FROM stickers",
    )
    abstract suspend fun diagnosticCounts(version: Int): DiagnosticCounts

    @Query(
        "SELECT * FROM stickers WHERE indexedAt IS NULL OR indexVersion < :version " +
            "ORDER BY indexAttempts ASC, lastModified DESC LIMIT :limit",
    )
    abstract suspend fun needingIndex(version: Int, limit: Int): List<StickerEntity>

    @Query("SELECT * FROM stickers WHERE id = :id")
    abstract suspend fun byId(id: Long): StickerEntity?

    @Query(
        "UPDATE stickers SET isAnimated = :isAnimated, perceptualHash = :perceptualHash, " +
            "ocrText = :ocrText, packName = :packName, packPublisher = :packPublisher, emojiWords = :emojiWords, " +
            "contentHash = COALESCE(:contentHash, contentHash), " +
            "indexedAt = :indexedAt, indexVersion = :indexVersion, indexAttempts = 0 WHERE id = :id",
    )
    abstract suspend fun saveIndexResult(
        id: Long,
        isAnimated: Boolean,
        perceptualHash: Long?,
        ocrText: String?,
        packName: String?,
        packPublisher: String?,
        emojiWords: String?,
        contentHash: String?,
        indexedAt: Long,
        indexVersion: Int,
    )

    @Query("UPDATE stickers SET indexAttempts = indexAttempts + 1 WHERE id = :id")
    abstract suspend fun markIndexAttempt(id: Long)

    /** Saves a batch of index results, with their search terms, in one transaction. */
    @Transaction
    open suspend fun saveIndexResults(results: List<IndexResult>) {
        for (r in results) {
            saveIndexResult(
                r.id, r.isAnimated, r.perceptualHash, r.ocrText, r.packName, r.packPublisher, r.emojiWords,
                r.contentHash, r.indexedAt, r.indexVersion,
            )
            refreshFts(r.id)
        }
    }

    /** Rebuilds the full-text entry of one sticker from its current text fields. */
    open suspend fun refreshFts(id: Long) {
        val s = byId(id) ?: return
        val terms = IndexTerms.build(
            s.ocrText, s.captionHe, s.captionEn, s.captionTags, s.visibleImageTags, s.userTags,
            s.packName, s.packPublisher, s.emojiWords, s.peopleNames, s.userDescription,
        )
        replaceFts(StickerFts(s.id, terms))
    }

    /** Indexed stickers the image model hasn't seen yet; favorites first. */
    @Query(
        "SELECT * FROM stickers WHERE imageTaggedAt IS NULL AND indexedAt IS NOT NULL " +
            "ORDER BY imageTagAttempts ASC, starred DESC, useCount DESC, lastModified DESC LIMIT :limit",
    )
    abstract suspend fun needingImageTags(limit: Int): List<StickerEntity>

    @Query("SELECT COUNT(*) FROM stickers WHERE imageTaggedAt IS NULL AND indexedAt IS NOT NULL")
    abstract fun observeImageTagPendingCount(): Flow<Int>

    @Query("UPDATE stickers SET imageTagAttempts = imageTagAttempts + 1 WHERE id = :id")
    abstract suspend fun markImageTagAttempt(id: Long)

    @Query(
        "UPDATE stickers SET imageTags = :tags, imageTaggedAt = :at, imageTagsVersion = :version, " +
            "imageTagAttempts = 0 WHERE id = :id",
    )
    abstract suspend fun saveImageTagsOnly(id: Long, tags: String?, at: Long, version: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun upsertImageVector(vector: StickerImageVector)

    /** Saves a sticker's picture tags, with its vector (null if it couldn't be read), and search terms. */
    @Transaction
    open suspend fun saveImageTags(id: Long, tags: String?, version: String, vector: StickerImageVector?) {
        saveImageTagsOnly(id, tags, System.currentTimeMillis(), version)
        if (vector != null) upsertImageVector(vector)
        refreshFts(id)
    }

    /** Tagged with an older label list; their tags are re-derived from the stored vectors. */
    @Query(
        "SELECT v.stickerId, v.vector FROM sticker_image_vectors v JOIN stickers s ON s.id = v.stickerId " +
            "WHERE v.model = :model AND s.imageTaggedAt IS NOT NULL AND s.imageTagsVersion != :version LIMIT :limit",
    )
    abstract suspend fun imageVectorsWithOldTags(model: String, version: String, limit: Int): List<StickerVectorRow>

    @Query("SELECT * FROM sticker_image_vectors WHERE stickerId = :id")
    abstract suspend fun imageVector(id: Long): StickerImageVector?

    /** Picture vectors in id order, a page at a time (all of them at once is tens of MB). */
    @Query(
        "SELECT stickerId, vector FROM sticker_image_vectors WHERE model = :model AND stickerId > :after " +
            "ORDER BY stickerId LIMIT :limit",
    )
    abstract suspend fun imageVectorPage(model: String, after: Long, limit: Int): List<StickerVectorRow>

    @Query("SELECT id FROM stickers WHERE packName = :pack")
    abstract suspend fun idsInPack(pack: String): List<Long>

    @Query("SELECT COUNT(*) FROM stickers WHERE packName = :pack")
    abstract suspend fun countInPack(pack: String): Int

    @Query("UPDATE stickers SET userDescription = :description WHERE id = :id")
    abstract suspend fun setUserDescription(id: Long, description: String?)

    @Query("SELECT * FROM search_picks WHERE queryKey = :queryKey AND stickerId = :stickerId")
    abstract suspend fun pick(queryKey: String, stickerId: Long): SearchPick?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun upsertPick(pick: SearchPick)

    /** Counts one more pick of [stickerId] for the search [queryKey]; returns the updated pick. */
    @Transaction
    open suspend fun recordPick(queryKey: String, stickerId: Long, at: Long): SearchPick {
        val old = pick(queryKey, stickerId)
        val updated = SearchPick(queryKey, stickerId, (old?.count ?: 0) + 1, at, tagged = old?.tagged ?: false)
        upsertPick(updated)
        return updated
    }

    @Query("UPDATE search_picks SET tagged = 1 WHERE queryKey = :queryKey AND stickerId = :stickerId")
    abstract suspend fun markPickTagged(queryKey: String, stickerId: Long)

    /** Stickers with learned tags, for reviewing them. */
    @Query("SELECT * FROM stickers WHERE learnedTags IS NOT NULL AND learnedTags != ''")
    abstract fun observeWithLearnedTags(): Flow<List<StickerEntity>>

    /** Every remembered pick; a few thousand rows at most, ranked in memory. */
    @Query("SELECT * FROM search_picks")
    abstract suspend fun allPicks(): List<SearchPick>

    /** Restoring a backup: keeps the higher use count and the later last use. */
    @Query(
        "UPDATE stickers SET useCount = MAX(useCount, :count), " +
            "lastUsedAt = CASE WHEN :lastUsedAt IS NULL THEN lastUsedAt ELSE MAX(COALESCE(lastUsedAt, 0), :lastUsedAt) END " +
            "WHERE id = :id",
    )
    abstract suspend fun mergeUse(id: Long, count: Int, lastUsedAt: Long?)

    /** Restoring a backup: adds a pick, keeping the higher count and the later time if it's known. */
    @Transaction
    open suspend fun mergePick(queryKey: String, stickerId: Long, count: Int, lastAt: Long) {
        val old = pick(queryKey, stickerId)
        upsertPick(
            SearchPick(
                queryKey, stickerId, maxOf(old?.count ?: 0, count), maxOf(old?.lastAt ?: 0, lastAt),
                tagged = old?.tagged ?: false,
            ),
        )
    }

    @Query("SELECT * FROM people")
    abstract suspend fun people(): List<Person>

    @Query("DELETE FROM search_picks")
    abstract suspend fun clearPicks()

    @Query("SELECT COUNT(*) FROM search_picks")
    abstract suspend fun pickCount(): Int

    @Query("SELECT COUNT(*) FROM sticker_image_vectors WHERE model = :model")
    abstract suspend fun imageVectorCount(model: String): Int

    /** The tag fields learned tags are computed from, for every sticker. */
    @Query("SELECT id, userTags, learnedTags, removedImageTags FROM stickers")
    abstract suspend fun tagStates(): List<StickerTagState>

    @Query("UPDATE stickers SET learnedTags = :tags WHERE id = :id")
    abstract suspend fun setLearnedTagsOnly(id: Long, tags: String?)

    /** Saves a sticker's learned tags and rebuilds its search terms. */
    @Transaction
    open suspend fun setLearnedTags(id: Long, tags: String?) {
        setLearnedTagsOnly(id, tags)
        refreshFts(id)
    }

    @Query("SELECT COUNT(*) FROM stickers WHERE learnedTags IS NOT NULL AND learnedTags != ''")
    abstract suspend fun countWithLearnedTags(): Int

    @Query("UPDATE stickers SET removedImageTags = :removed WHERE id = :id")
    abstract suspend fun setRemovedImageTags(id: Long, removed: String?)

    /** The sticker's meaning vector for "same context": what it shows if it has one, else what it says. */
    @Query(
        "SELECT * FROM sticker_vectors WHERE stickerId = :id " +
            "ORDER BY CASE facet WHEN 2 THEN 0 WHEN 1 THEN 1 ELSE 2 END LIMIT 1",
    )
    abstract suspend fun meaningVector(id: Long): StickerVector?

    /** Meaning vectors of one facet in id order, a page at a time. */
    @Query(
        "SELECT stickerId, vector FROM sticker_vectors WHERE model = :model AND facet = :facet AND stickerId > :after " +
            "ORDER BY stickerId LIMIT :limit",
    )
    abstract suspend fun meaningVectorPage(model: String, facet: Int, after: Long, limit: Int): List<StickerVectorRow>

    /** Other stickers with a face from the same group as a face on sticker [id]. */
    @Query(
        "SELECT DISTINCT f2.stickerId FROM sticker_faces f1 JOIN sticker_faces f2 ON f2.personId = f1.personId " +
            "WHERE f1.stickerId = :id AND f1.personId IS NOT NULL AND f2.stickerId != :id",
    )
    abstract suspend fun samePersonStickers(id: Long): List<Long>

    @Query(
        "SELECT f.id AS id, f.stickerId AS stickerId, s.documentUri AS documentUri, f.x0 AS x0, f.y0 AS y0, " +
            "f.x1 AS x1, f.y1 AS y1, f.personId AS personId, p.name AS name " +
            "FROM sticker_faces f JOIN stickers s ON s.id = f.stickerId LEFT JOIN people p ON p.id = f.personId " +
            "WHERE f.stickerId = :id ORDER BY f.x0",
    )
    abstract fun observeFacesOnSticker(id: Long): Flow<List<StickerFaceInfo>>

    @Query("SELECT * FROM stickers WHERE id = :id")
    abstract fun observeSticker(id: Long): Flow<StickerEntity?>

    @Query("UPDATE stickers SET userTags = :tags WHERE id = :id")
    abstract suspend fun setTags(id: Long, tags: String)

    @Query("UPDATE stickers SET starred = :starred WHERE id = :id")
    abstract suspend fun setStarred(id: Long, starred: Boolean)

    @Query("UPDATE stickers SET useCount = useCount + 1, lastUsedAt = :at WHERE id = :id")
    abstract suspend fun recordUse(id: Long, at: Long)

    @Insert
    abstract suspend fun insertFts(row: StickerFts)

    /** FTS4 tables don't reliably support INSERT OR REPLACE, so delete then insert. */
    @Transaction
    open suspend fun replaceFts(row: StickerFts) {
        deleteFts(listOf(row.rowId))
        insertFts(row)
    }

    @Query(
        "SELECT s.* FROM stickers s JOIN sticker_fts f ON s.id = f.rowid " +
            "WHERE sticker_fts MATCH :match " +
            "ORDER BY s.starred DESC, s.useCount DESC, s.lastModified DESC LIMIT :limit",
    )
    abstract suspend fun searchFts(match: String, limit: Int): List<StickerEntity>

    /** [searchFts] among one folder's stickers only. */
    @Query(
        "SELECT s.* FROM stickers s JOIN sticker_fts f ON s.id = f.rowid " +
            "WHERE sticker_fts MATCH :match AND s.id IN (SELECT stickerId FROM folder_stickers WHERE folderId = :folderId) " +
            "ORDER BY s.starred DESC, s.useCount DESC, s.lastModified DESC LIMIT :limit",
    )
    abstract suspend fun searchFtsInFolder(match: String, folderId: Long, limit: Int): List<StickerEntity>

    /** Shown when the query is empty: starred first, then most used, then newest. */
    @Query(
        "SELECT * FROM stickers " +
            "ORDER BY starred DESC, useCount DESC, lastModified DESC LIMIT :limit",
    )
    abstract fun observeBrowse(limit: Int): Flow<List<StickerEntity>>

    @Query("SELECT COUNT(*) FROM stickers")
    abstract fun observeCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM stickers WHERE indexedAt IS NULL OR indexVersion < :version")
    abstract fun observePendingCount(version: Int): Flow<Int>

    // --- Faces and people ---

    /** Indexed stickers not yet scanned for faces; the ones that failed before go last. */
    @Query(
        "SELECT * FROM stickers WHERE facesScannedAt IS NULL AND indexedAt IS NOT NULL " +
            "ORDER BY faceScanAttempts ASC, lastModified DESC LIMIT :limit",
    )
    abstract suspend fun needingFaceScan(limit: Int): List<StickerEntity>

    @Query("SELECT COUNT(*) FROM stickers WHERE facesScannedAt IS NULL")
    abstract fun observeFaceScanPendingCount(): Flow<Int>

    @Query("UPDATE stickers SET faceScanAttempts = faceScanAttempts + 1 WHERE id = :id")
    abstract suspend fun markFaceScanAttempt(id: Long)

    @Query("UPDATE stickers SET facesScannedAt = :at, faceScanAttempts = 0 WHERE id = :id")
    abstract suspend fun markFacesScanned(id: Long, at: Long)

    @Insert
    abstract suspend fun insertFaces(faces: List<StickerFace>)

    /** Replaces a sticker's faces with what the scan found (possibly none). */
    @Transaction
    open suspend fun saveFaces(stickerId: Long, faces: List<StickerFace>) {
        deleteFacesOf(listOf(stickerId))
        if (faces.isNotEmpty()) insertFaces(faces)
        markFacesScanned(stickerId, System.currentTimeMillis())
    }

    @Query("SELECT id, personId, locked, vector FROM sticker_faces")
    abstract suspend fun faceRows(): List<FaceRow>

    @Insert
    abstract suspend fun insertPerson(person: Person): Long

    @Query("UPDATE sticker_faces SET personId = :personId WHERE id IN (:faceIds)")
    abstract suspend fun assignFacesChunk(faceIds: List<Long>, personId: Long)

    @Transaction
    open suspend fun assignFaces(faceIds: List<Long>, personId: Long) {
        faceIds.chunked(500).forEach { assignFacesChunk(it, personId) }
    }

    /** Groups with at least [minStickers] stickers, named ones first, then the biggest. */
    @Query(
        "SELECT p.id AS id, p.name AS name, COUNT(DISTINCT f.stickerId) AS stickers, COUNT(f.id) AS faces " +
            "FROM people p JOIN sticker_faces f ON f.personId = p.id GROUP BY p.id " +
            "HAVING COUNT(DISTINCT f.stickerId) >= :minStickers " +
            "ORDER BY (p.name IS NULL) ASC, stickers DESC",
    )
    abstract fun observePeople(minStickers: Int): Flow<List<PersonSummary>>

    @Query(
        "SELECT f.id AS id, f.stickerId AS stickerId, s.documentUri AS documentUri, " +
            "f.x0 AS x0, f.y0 AS y0, f.x1 AS x1, f.y1 AS y1 " +
            "FROM sticker_faces f JOIN stickers s ON s.id = f.stickerId WHERE f.personId = :personId " +
            "ORDER BY f.id LIMIT :limit",
    )
    abstract fun observeFacesOf(personId: Long, limit: Int): Flow<List<FaceOnSticker>>

    @Query("UPDATE people SET name = :name WHERE id = :id")
    abstract suspend fun renamePerson(id: Long, name: String?)

    /** Takes a face out of its group for good (the user said it's someone else). */
    @Query("UPDATE sticker_faces SET personId = NULL, locked = 1 WHERE id = :faceId")
    abstract suspend fun removeFaceFromGroup(faceId: Long)

    @Query("UPDATE sticker_faces SET personId = :into WHERE personId = :from")
    abstract suspend fun moveFaces(from: Long, into: Long)

    @Query("DELETE FROM people WHERE id NOT IN (SELECT DISTINCT personId FROM sticker_faces WHERE personId IS NOT NULL)")
    abstract suspend fun deleteEmptyPeople()

    @Query("SELECT * FROM people WHERE id = :id")
    abstract suspend fun person(id: Long): Person?

    /** Merges group [from] into [into]; [into] keeps its name, or takes [from]'s if it has none. */
    @Transaction
    open suspend fun mergePeople(from: Long, into: Long) {
        if (from == into) return
        val name = person(into)?.name ?: person(from)?.name
        moveFaces(from, into)
        renamePerson(into, name)
        deleteEmptyPeople()
    }

    /** Stickers whose [StickerEntity.peopleNames] no longer match their faces' names. */
    @Query(
        "SELECT id FROM stickers WHERE COALESCE(peopleNames, '') != COALESCE((SELECT group_concat(DISTINCT p.name) " +
            "FROM sticker_faces f JOIN people p ON p.id = f.personId " +
            "WHERE f.stickerId = stickers.id AND p.name IS NOT NULL), '')",
    )
    abstract suspend fun stickersWithStalePeopleNames(): List<Long>

    @Query(
        "UPDATE stickers SET peopleNames = (SELECT group_concat(DISTINCT p.name) " +
            "FROM sticker_faces f JOIN people p ON p.id = f.personId " +
            "WHERE f.stickerId = stickers.id AND p.name IS NOT NULL) WHERE id = :id",
    )
    abstract suspend fun updatePeopleNames(id: Long)

    /** Brings stickers' searchable people names in step with the groups; returns how many changed. */
    @Transaction
    open suspend fun syncPeopleNames(): Int {
        val stale = stickersWithStalePeopleNames()
        for (id in stale) {
            updatePeopleNames(id)
            refreshFts(id)
        }
        return stale.size
    }

    @Query("DELETE FROM sticker_faces")
    abstract suspend fun deleteAllFaces()

    @Query("DELETE FROM people")
    abstract suspend fun deleteAllPeople()

    @Query("UPDATE stickers SET facesScannedAt = NULL, faceScanAttempts = 0")
    abstract suspend fun resetFaceScans()

    @Query("UPDATE sticker_faces SET personId = NULL, locked = 0")
    abstract suspend fun ungroupAllFaces()

    /** Drops every group (and its name), keeping the faces to be grouped again. */
    @Transaction
    open suspend fun resetGroups() {
        ungroupAllFaces()
        deleteAllPeople()
        syncPeopleNames()
    }

    /** Deletes every face and group, and the names they made searchable. */
    @Transaction
    open suspend fun deleteFaceData() {
        deleteAllFaces()
        deleteAllPeople()
        resetFaceScans()
        syncPeopleNames()
    }

    /**
     * Every file that is exactly the same sticker as one of [ids] (same bytes: WhatsApp keeps
     * copies), and [ids] themselves. Not by perceptual hash: different pictures can share one.
     */
    @Query(
        "SELECT id FROM stickers WHERE id IN (:ids) OR contentHash IN " +
            "(SELECT contentHash FROM stickers WHERE id IN (:ids) AND contentHash IS NOT NULL AND contentHash != '')",
    )
    abstract suspend fun withCopies(ids: List<Long>): List<Long>

    /** Indexed stickers whose content hash isn't known yet (from before it was kept). */
    @Query("SELECT id, documentUri FROM stickers WHERE contentHash IS NULL AND indexedAt IS NOT NULL ORDER BY id LIMIT :limit")
    abstract suspend fun withoutContentHash(limit: Int): List<StickerUri>

    @Query("UPDATE stickers SET contentHash = :hash WHERE id = :id")
    abstract suspend fun setContentHash(id: Long, hash: String)

    /** Content hashes of the library, for recognizing files by their exact bytes. */
    @Query("SELECT id, contentHash FROM stickers WHERE contentHash IS NOT NULL AND contentHash != ''")
    abstract suspend fun contentHashes(): List<StickerContentHash>

    // --- Folders ------------------------------------------------------------------------------

    @Query(
        "SELECT f.id AS id, f.name AS name, COUNT(fs.stickerId) AS count FROM folders f " +
            "LEFT JOIN folder_stickers fs ON fs.folderId = f.id GROUP BY f.id ORDER BY f.name COLLATE NOCASE",
    )
    abstract fun observeFolders(): Flow<List<FolderSummary>>

    @Query("SELECT * FROM folders ORDER BY name COLLATE NOCASE")
    abstract suspend fun folders(): List<Folder>

    /** A folder's stickers, most recently added first. */
    @Query(
        "SELECT s.* FROM stickers s JOIN folder_stickers fs ON fs.stickerId = s.id " +
            "WHERE fs.folderId = :folderId ORDER BY fs.addedAt DESC",
    )
    abstract fun observeFolderStickers(folderId: Long): Flow<List<StickerEntity>>

    @Query("SELECT stickerId FROM folder_stickers WHERE folderId = :folderId")
    abstract suspend fun folderStickerIds(folderId: Long): List<Long>

    /** Every folder membership, for backups. */
    @Query("SELECT * FROM folder_stickers")
    abstract suspend fun folderStickers(): List<FolderSticker>

    @Query("SELECT folderId FROM folder_stickers WHERE stickerId = :stickerId")
    abstract fun observeFoldersOf(stickerId: Long): Flow<List<Long>>

    @Insert
    abstract suspend fun insertFolder(folder: Folder): Long

    @Query("UPDATE folders SET name = :name WHERE id = :id")
    abstract suspend fun renameFolder(id: Long, name: String)

    @Query("DELETE FROM folders WHERE id = :id")
    abstract suspend fun deleteFolderRow(id: Long)

    @Query("DELETE FROM folder_stickers WHERE folderId = :folderId")
    abstract suspend fun emptyFolder(folderId: Long)

    /** Deletes a folder; its stickers stay where they are, only the grouping goes. */
    @Transaction
    open suspend fun deleteFolder(id: Long) {
        emptyFolder(id)
        deleteFolderRow(id)
    }

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    abstract suspend fun addToFolder(items: List<FolderSticker>)

    @Query("DELETE FROM folder_stickers WHERE folderId = :folderId AND stickerId = :stickerId")
    abstract suspend fun removeFromFolder(folderId: Long, stickerId: Long)
}

/** What the indexer found for one sticker; see [StickerDao.saveIndexResult]. */
data class IndexResult(
    val id: Long,
    val isAnimated: Boolean,
    val perceptualHash: Long?,
    val ocrText: String?,
    val packName: String?,
    val packPublisher: String?,
    val emojiWords: String?,
    /** SHA-256 of the file (hex), or null when this pass didn't read the whole file. */
    val contentHash: String?,
    val indexedAt: Long,
    val indexVersion: Int,
)

/** A sticker and its file, for a pass that reads files. */
data class StickerUri(val id: Long, val documentUri: String)

/** A sticker's exact content hash. */
data class StickerContentHash(val id: Long, val contentHash: String)
