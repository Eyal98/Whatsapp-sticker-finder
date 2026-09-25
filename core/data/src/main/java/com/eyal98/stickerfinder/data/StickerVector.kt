package com.eyal98.stickerfinder.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/** A sticker's meaning vector (see [com.eyal98.stickerfinder.search.Vectors]). */
@Entity(tableName = "sticker_vectors")
class StickerVector(
    @PrimaryKey val stickerId: Long,
    /** Vectors from different models can't be compared, so each one records its model. */
    val model: String,
    /** Fingerprint of the text that was embedded; a different value means it's out of date. */
    val fingerprint: Long,
    /** Little-endian float32s. */
    val vector: ByteArray,
)

class StickerVectorRow(val stickerId: Long, val vector: ByteArray)

data class StickerVectorState(val stickerId: Long, val model: String, val fingerprint: Long)

/** Changes whenever any vector is added, replaced or removed. */
data class VectorSignature(val count: Int, val total: Double)
