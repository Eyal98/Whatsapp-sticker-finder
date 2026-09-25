package com.eyal98.stickerfinder.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** One sticker file found in the WhatsApp Stickers folder, plus everything we learned about it. */
@Entity(
    tableName = "stickers",
    indices = [Index(value = ["documentUri"], unique = true)],
)
data class StickerEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** SAF document URI inside the granted tree. Read-only. */
    val documentUri: String,
    val displayName: String,
    val sizeBytes: Long,
    val lastModified: Long,
    val isAnimated: Boolean = false,
    /** 64-bit dHash, used to hide duplicate copies of the same sticker. */
    val perceptualHash: Long? = null,
    /** Text printed on the sticker (OCR, Phase 1). */
    val ocrText: String? = null,
    /** On-device model descriptions (Phase 2). */
    val captionHe: String? = null,
    val captionEn: String? = null,
    /** Space-separated tags the user typed. */
    val userTags: String = "",
    /** The user's own "favorite" mark, since WhatsApp's favorites list can't be read. */
    val starred: Boolean = false,
    val useCount: Int = 0,
    val lastUsedAt: Long? = null,
    /** When the indexer last processed this file. Null means it still needs indexing. */
    val indexedAt: Long? = null,
    /** Which [IndexVersion] produced the indexed fields; older rows are processed again. */
    @ColumnInfo(defaultValue = "0") val indexVersion: Int = 0,
)

/** What the indexer extracts. Bump [CURRENT] when it learns something new, to re-index old rows. */
object IndexVersion {
    /** Animated flag and duplicate hash. */
    const val BASIC = 1

    /** Adds text read from the sticker (OCR). */
    const val OCR = 2

    const val CURRENT = OCR
}

/** The subset of columns the folder scanner needs to detect new, changed and removed files. */
data class StickerFileState(
    val id: Long,
    val documentUri: String,
    val sizeBytes: Long,
    val lastModified: Long,
)
