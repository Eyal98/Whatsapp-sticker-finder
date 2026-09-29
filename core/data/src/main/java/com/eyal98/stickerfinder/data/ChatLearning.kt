package com.eyal98.stickerfinder.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * What a sticker is used for, learned from the user's imported chats: the average meaning of the
 * messages just before it was sent. Only this vector and how many sends it averages are kept,
 * never the messages. Kept apart from [StickerVector], whose rows the embedding pass rewrites.
 */
@Entity(tableName = "sticker_contexts")
class StickerContext(
    @PrimaryKey val stickerId: Long,
    /** The embedding model; a different model's vectors can't be compared, so they're replaced. */
    val model: String,
    /** How many sticker sends [vector] averages, to weigh the next chat against them. */
    val uses: Int,
    /** Little-endian float32s: the running average, not normalized. */
    val vector: ByteArray,
)

/**
 * A chat export already learned from, known only by a hash of its chat text, so importing the
 * same export again doesn't count its stickers twice.
 */
@Entity(tableName = "imported_chats")
class ImportedChat(
    @PrimaryKey val hash: String,
    val importedAt: Long,
)

/** One chat's lesson for one sticker: the mean of [uses] sends' context vectors. */
class ContextUpdate(val stickerId: Long, val uses: Int, val mean: FloatArray)

/** A context vector with its sticker's pack, for building the search index. */
class ContextIndexRow(val stickerId: Long, val vector: ByteArray, val packName: String?)

/** A sticker's perceptual hash, for recognizing it in a chat export. */
class StickerHashRow(val id: Long, val perceptualHash: Long)
