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

    /** Records a changed file and marks it for re-indexing. */
    @Query(
        "UPDATE stickers SET sizeBytes = :sizeBytes, lastModified = :lastModified, " +
            "indexedAt = NULL WHERE id = :id",
    )
    abstract suspend fun markChanged(id: Long, sizeBytes: Long, lastModified: Long)

    @Query("DELETE FROM stickers WHERE id IN (:ids)")
    abstract suspend fun deleteStickers(ids: List<Long>)

    @Query("DELETE FROM sticker_fts WHERE rowid IN (:ids)")
    abstract suspend fun deleteFts(ids: List<Long>)

    @Transaction
    open suspend fun deleteWithFts(ids: List<Long>) {
        // SQLite limits bound parameters per statement, so delete in chunks.
        ids.chunked(500).forEach {
            deleteFts(it)
            deleteStickers(it)
        }
    }

    @Query(
        "SELECT * FROM stickers WHERE indexedAt IS NULL OR indexVersion < :version " +
            "ORDER BY lastModified DESC LIMIT :limit",
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
