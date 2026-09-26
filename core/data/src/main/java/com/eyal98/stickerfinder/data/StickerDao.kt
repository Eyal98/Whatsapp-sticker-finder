package com.eyal98.stickerfinder.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.eyal98.stickerfinder.search.IndexTerms
import kotlinx.coroutines.flow.Flow

@Dao
abstract class StickerDao {

    @Query("SELECT id, documentUri, sizeBytes, lastModified FROM stickers")
    abstract suspend fun fileStates(): List<StickerFileState>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    abstract suspend fun insertAll(stickers: List<StickerEntity>)

    /** Records a changed file and marks it for re-indexing and re-captioning. */
    @Query(
        "UPDATE stickers SET sizeBytes = :sizeBytes, lastModified = :lastModified, " +
            "indexedAt = NULL, captionedAt = NULL, imageTaggedAt = NULL, " +
            "indexAttempts = 0, captionAttempts = 0, imageTagAttempts = 0 WHERE id = :id",
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

    /** Deletes stickers together with their search index rows and vectors. */
    @Transaction
    open suspend fun deleteWithFts(ids: List<Long>) {
        // SQLite limits bound parameters per statement, so delete in chunks.
        ids.chunked(500).forEach {
            deleteFts(it)
            deleteVectors(it)
            deleteImageVectors(it)
            deleteStickers(it)
        }
    }

    @Query("SELECT * FROM stickers")
    abstract suspend fun allStickers(): List<StickerEntity>

    @Query("SELECT * FROM stickers WHERE id IN (:ids)")
    abstract suspend fun byIds(ids: List<Long>): List<StickerEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun upsertVector(vector: StickerVector)

    @Query("SELECT stickerId, model, fingerprint FROM sticker_vectors")
    abstract suspend fun vectorStates(): List<StickerVectorState>

    @Query("SELECT stickerId, vector FROM sticker_vectors WHERE model = :model")
    abstract suspend fun vectors(model: String): List<StickerVectorRow>

    @Query("SELECT COUNT(*) AS count, TOTAL(fingerprint) + TOTAL(stickerId) AS total FROM sticker_vectors")
    abstract suspend fun vectorSignature(): VectorSignature

    @Query("SELECT COUNT(*) FROM sticker_vectors")
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
            "(SELECT COUNT(*) FROM sticker_vectors) AS vectors " +
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
                r.indexedAt, r.indexVersion,
            )
            refreshFts(r.id)
        }
    }

    /** Rebuilds the full-text entry of one sticker from its current text fields. */
    open suspend fun refreshFts(id: Long) {
        val s = byId(id) ?: return
        val terms = IndexTerms.build(
            s.ocrText, s.captionHe, s.captionEn, s.captionTags, s.imageTags, s.userTags,
            s.packName, s.packPublisher, s.emojiWords,
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
    val indexedAt: Long,
    val indexVersion: Int,
)
