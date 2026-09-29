package com.eyal98.stickerfinder.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** A folder the user made and named, to keep stickers together and browse them by hand. */
@Entity(tableName = "folders")
data class Folder(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val createdAt: Long,
)

/** A sticker in a folder. A sticker can be in any number of folders. */
@Entity(tableName = "folder_stickers", primaryKeys = ["folderId", "stickerId"], indices = [Index("stickerId")])
data class FolderSticker(
    val folderId: Long,
    val stickerId: Long,
    val addedAt: Long,
)

/** A folder with how many stickers it holds, for the folder chips. */
data class FolderSummary(val id: Long, val name: String, val count: Int)
