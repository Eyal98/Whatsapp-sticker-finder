package com.eyal98.stickerfinder.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
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
            "indexedAt = NULL, captionedAt = NULL, indexAttempts = 0, captionAttempts = 0 WHERE id = :id",
    )
    abstract suspend fun markChanged(id: Long, sizeBytes: Long, lastModified: Long)

    @Query("DELETE FROM stickers WHERE id IN (:ids)")
    abstract suspend fun deleteStickers(ids: List<Long>)

    @Query("DELETE FROM sticker_fts WHERE rowid IN (:ids)")
    abstract suspend fun deleteFts(ids: List<Long>)

    @Query("DELETE FROM sticker_vectors WHERE stickerId IN (:ids)")
    abstract suspend fun deleteVectors(ids: List<Long>)

    /** Deletes stickers together with their search index rows and vectors. */
    @Transaction
    open suspend fun deleteWithFts(ids: List<Long>) {
        // SQLite limits bound parameters per statement, so delete in chunks.
        ids.chunked(500).forEach {
            deleteFts(it)
            deleteVectors(it)
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
            "COALESCE(SUM(CASE WHEN indexedAt IS NOT NULL AND indexVersion >= :version THEN 1 ELSE 0 END), 0) AS indexed, " +
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
            "ocrText = :ocrText, indexedAt = :indexedAt, indexVersion = :indexVersion WHERE id = :id",
    )
    abstract suspend fun saveIndexResult(
        id: Long,
        isAnimated: Boolean,
        perceptualHash: Long?,
        ocrText: String?,
        indexedAt: Long,
        indexVersion: Int,
    )

    /** Stickers the basic indexer is done with but the caption model hasn't seen; favorites first. */
    @Query(
        "SELECT * FROM stickers WHERE captionedAt IS NULL AND indexedAt IS NOT NULL " +
            "ORDER BY captionAttempts ASC, starred DESC, useCount DESC, lastModified DESC LIMIT :limit",
    )
    abstract suspend fun needingCaption(limit: Int): List<StickerEntity>

    @Query(
        "UPDATE stickers SET captionEn = :en, captionHe = :he, captionTags = :tags, " +
            "captionedAt = :at, captionModel = :model WHERE id = :id",
    )
    abstract suspend fun saveCaption(id: Long, en: String?, he: String?, tags: String?, at: Long, model: String)

    @Query("SELECT COUNT(*) FROM stickers WHERE captionedAt IS NULL")
    abstract fun observeCaptionPendingCount(): Flow<Int>

    @Query("UPDATE stickers SET indexAttempts = indexAttempts + 1 WHERE id = :id")
    abstract suspend fun markIndexAttempt(id: Long)

    @Query("UPDATE stickers SET captionAttempts = captionAttempts + 1 WHERE id = :id")
    abstract suspend fun markCaptionAttempt(id: Long)

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
