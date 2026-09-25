package com.eyal98.stickerfinder.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [StickerEntity::class, StickerFts::class],
    version = 2,
    exportSchema = true,
)
abstract class StickerDatabase : RoomDatabase() {

    abstract fun stickerDao(): StickerDao

    companion object {
        private const val NAME = "stickers.db"

        // TODO(Phase 4): encrypt at rest with SQLCipher, key wrapped by Android Keystore.
        fun create(context: Context): StickerDatabase =
            Room.databaseBuilder(context.applicationContext, StickerDatabase::class.java, NAME)
                .addMigrations(MIGRATION_1_2)
                .build()

        /** Adds [StickerEntity.indexVersion]; existing rows start at 0 so they get OCR'd. */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE stickers ADD COLUMN indexVersion INTEGER NOT NULL DEFAULT 0")
            }
        }
    }
}
