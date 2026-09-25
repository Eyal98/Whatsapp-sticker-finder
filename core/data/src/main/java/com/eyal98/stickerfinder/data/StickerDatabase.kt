package com.eyal98.stickerfinder.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [StickerEntity::class, StickerFts::class],
    version = 1,
    exportSchema = true,
)
abstract class StickerDatabase : RoomDatabase() {

    abstract fun stickerDao(): StickerDao

    companion object {
        private const val NAME = "stickers.db"

        // TODO(Phase 4): encrypt at rest with SQLCipher, key wrapped by Android Keystore.
        fun create(context: Context): StickerDatabase =
            Room.databaseBuilder(context.applicationContext, StickerDatabase::class.java, NAME)
                .build()
    }
}
