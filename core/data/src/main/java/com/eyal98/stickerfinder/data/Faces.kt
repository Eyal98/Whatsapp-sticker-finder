package com.eyal98.stickerfinder.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A face found on a sticker, with its SFace identity vector. Biometric data: it never leaves the
 * phone (the app has no network access, and its data is excluded from backups), and the People
 * screen can delete all of it.
 */
@Entity(tableName = "sticker_faces", indices = [Index("stickerId"), Index("personId")])
class StickerFace(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val stickerId: Long,
    /** The face's box, as fractions of the sticker's width and height. */
    val x0: Float,
    val y0: Float,
    val x1: Float,
    val y1: Float,
    /** Little-endian float32s, L2-normalized. */
    val vector: ByteArray,
    /** The group it belongs to; null while it matches no one. */
    val personId: Long? = null,
    /** Removed from a group by the user: never grouped automatically again. */
    @ColumnInfo(defaultValue = "0") val locked: Boolean = false,
)

/** A group of faces of one person; [name] is what the user calls them, searchable. */
@Entity(tableName = "people")
data class Person(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String? = null,
)

/** A face vector for grouping. */
class FaceRow(val id: Long, val personId: Long?, val locked: Boolean, val vector: ByteArray)

/** One group for the People screen. */
data class PersonSummary(val id: Long, val name: String?, val stickers: Int, val faces: Int)

/** A face on one sticker with its group, for the sticker's details. */
data class StickerFaceInfo(
    val id: Long,
    val stickerId: Long,
    val documentUri: String,
    val x0: Float,
    val y0: Float,
    val x1: Float,
    val y1: Float,
    val personId: Long?,
    val name: String?,
) {
    fun asFace() = FaceOnSticker(id, stickerId, documentUri, x0, y0, x1, y1)
}

/** A face with the sticker it's on, to show a crop of it. */
data class FaceOnSticker(
    val id: Long,
    val stickerId: Long,
    val documentUri: String,
    val x0: Float,
    val y0: Float,
    val x1: Float,
    val y1: Float,
)
