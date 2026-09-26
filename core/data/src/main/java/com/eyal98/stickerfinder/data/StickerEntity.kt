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
    /** Comma-separated search keywords from the model, in both languages. */
    val captionTags: String? = null,
    /** When the model last looked at this sticker, even if it produced nothing. Null = pending. */
    val captionedAt: Long? = null,
    /** Which model wrote the caption. */
    val captionModel: String? = null,
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
    /**
     * Times indexing started on this file without finishing. Counted before the work starts, so
     * a file that crashes or hangs the native decoder/OCR goes to the back of the queue and is
     * eventually indexed without the step that fails, instead of blocking everything after it.
     */
    @ColumnInfo(defaultValue = "0") val indexAttempts: Int = 0,
    /** The same, for captioning. */
    @ColumnInfo(defaultValue = "0") val captionAttempts: Int = 0,
    /** Comma-separated picture tags (SigLIP2 image model + fixed label list), both languages. */
    val imageTags: String? = null,
    /** When the image model last looked at this sticker, even if no tag fit. Null = pending. */
    val imageTaggedAt: Long? = null,
    /** Which label list chose [imageTags]; a different one re-derives them from the stored vector. */
    val imageTagsVersion: String? = null,
    /** The same as [indexAttempts], for the image model. */
    @ColumnInfo(defaultValue = "0") val imageTagAttempts: Int = 0,
    /** The sticker pack's name and publisher, from the file's own metadata. */
    val packName: String? = null,
    val packPublisher: String? = null,
    /** Words for the emojis the pack filed this sticker under, in both languages. */
    val emojiWords: String? = null,
    /** Names the user gave the people whose faces are on it (see [StickerFace]), comma-separated. */
    val peopleNames: String? = null,
    /** When faces were last looked for. Null = pending. */
    val facesScannedAt: Long? = null,
    /** The same as [indexAttempts], for face scanning. */
    @ColumnInfo(defaultValue = "0") val faceScanAttempts: Int = 0,
)

/** What the indexer extracts. Bump [CURRENT] when it learns something new, to re-index old rows. */
object IndexVersion {
    /** Animated flag and duplicate hash. */
    const val BASIC = 1

    /** Adds text read from the sticker (OCR). */
    const val OCR = 2

    /** Adds the sticker-pack name, publisher and emojis from the file's metadata. */
    const val PACK = 3

    const val CURRENT = PACK
}

/** The subset of columns the folder scanner needs to detect new, changed and removed files. */
data class StickerFileState(
    val id: Long,
    val documentUri: String,
    val sizeBytes: Long,
    val lastModified: Long,
)
