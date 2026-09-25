package com.eyal98.stickerfinder.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Fts4
import androidx.room.PrimaryKey

/**
 * Full-text index. `rowid` equals [StickerEntity.id]; `terms` holds the normalized tokens built by
 * [com.eyal98.stickerfinder.search.IndexTerms] from OCR text, captions and user tags.
 */
@Fts4
@Entity(tableName = "sticker_fts")
data class StickerFts(
    @PrimaryKey @ColumnInfo(name = "rowid") val rowId: Long,
    val terms: String,
)
