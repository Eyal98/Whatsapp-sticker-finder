package com.eyal98.stickerfinder.data

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
)

/** The subset of columns the folder scanner needs to detect new, changed and removed files. */
data class StickerFileState(
    val id: Long,
    val documentUri: String,
    val sizeBytes: Long,
    val lastModified: Long,
)
