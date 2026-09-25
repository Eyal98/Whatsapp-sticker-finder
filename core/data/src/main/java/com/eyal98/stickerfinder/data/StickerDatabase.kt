package com.eyal98.stickerfinder.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [StickerEntity::class, StickerFts::class, StickerVector::class],
    version = 4,
    exportSchema = true,
)
abstract class StickerDatabase : RoomDatabase() {

    abstract fun stickerDao(): StickerDao

    companion object {
        private const val NAME = "stickers.db"

        // TODO(Phase 4): encrypt at rest with SQLCipher, key wrapped by Android Keystore.
        fun create(context: Context): StickerDatabase =
            Room.databaseBuilder(context.applicationContext, StickerDatabase::class.java, NAME)
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
                .build()

        /** Adds [StickerEntity.indexVersion]; existing rows start at 0 so they get OCR'd. */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE stickers ADD COLUMN indexVersion INTEGER NOT NULL DEFAULT 0")
            }
        }

        /** Adds the caption columns (Phase 2); every existing sticker starts as not captioned. */
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE stickers ADD COLUMN captionTags TEXT")
                db.execSQL("ALTER TABLE stickers ADD COLUMN captionedAt INTEGER")
                db.execSQL("ALTER TABLE stickers ADD COLUMN captionModel TEXT")
            }
        }

        /** Adds meaning vectors for semantic search. */
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `sticker_vectors` (`stickerId` INTEGER NOT NULL, " +
                        "`model` TEXT NOT NULL, `fingerprint` INTEGER NOT NULL, `vector` BLOB NOT NULL, " +
                        "PRIMARY KEY(`stickerId`))",
                )
            }
        }
    }
}
