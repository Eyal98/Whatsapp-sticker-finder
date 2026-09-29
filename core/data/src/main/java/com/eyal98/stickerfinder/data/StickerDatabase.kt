package com.eyal98.stickerfinder.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        StickerEntity::class, StickerFts::class, StickerVector::class, StickerImageVector::class,
        StickerFace::class, Person::class, SearchPick::class, StickerContext::class, ImportedChat::class,
    ],
    version = 14,
    exportSchema = true,
)
abstract class StickerDatabase : RoomDatabase() {

    abstract fun stickerDao(): StickerDao

    companion object {
        private const val NAME = "stickers.db"

        // TODO(Phase 4): encrypt at rest with SQLCipher, key wrapped by Android Keystore.
        fun create(context: Context): StickerDatabase =
            Room.databaseBuilder(context.applicationContext, StickerDatabase::class.java, NAME)
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10, MIGRATION_10_11, MIGRATION_11_12, MIGRATION_12_13, MIGRATION_13_14)
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

        /** Adds per-sticker attempt counters so one bad file can't stall indexing. */
        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE stickers ADD COLUMN indexAttempts INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE stickers ADD COLUMN captionAttempts INTEGER NOT NULL DEFAULT 0")
            }
        }

        /** Adds picture tags from the SigLIP2 image model, and their vectors. */
        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE stickers ADD COLUMN imageTags TEXT")
                db.execSQL("ALTER TABLE stickers ADD COLUMN imageTaggedAt INTEGER")
                db.execSQL("ALTER TABLE stickers ADD COLUMN imageTagsVersion TEXT")
                db.execSQL("ALTER TABLE stickers ADD COLUMN imageTagAttempts INTEGER NOT NULL DEFAULT 0")
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `sticker_image_vectors` (`stickerId` INTEGER NOT NULL, " +
                        "`model` TEXT NOT NULL, `vector` BLOB NOT NULL, PRIMARY KEY(`stickerId`))",
                )
            }
        }

        /** Adds the sticker-pack metadata; indexVersion < PACK makes the indexer fill it in. */
        val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE stickers ADD COLUMN packName TEXT")
                db.execSQL("ALTER TABLE stickers ADD COLUMN packPublisher TEXT")
                db.execSQL("ALTER TABLE stickers ADD COLUMN emojiWords TEXT")
            }
        }

        /** Adds faces and the people they're grouped into, for the People screen. */
        val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE stickers ADD COLUMN peopleNames TEXT")
                db.execSQL("ALTER TABLE stickers ADD COLUMN facesScannedAt INTEGER")
                db.execSQL("ALTER TABLE stickers ADD COLUMN faceScanAttempts INTEGER NOT NULL DEFAULT 0")
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `sticker_faces` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`stickerId` INTEGER NOT NULL, `x0` REAL NOT NULL, `y0` REAL NOT NULL, `x1` REAL NOT NULL, " +
                        "`y1` REAL NOT NULL, `vector` BLOB NOT NULL, `personId` INTEGER, " +
                        "`locked` INTEGER NOT NULL DEFAULT 0)",
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_sticker_faces_stickerId` ON `sticker_faces` (`stickerId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_sticker_faces_personId` ON `sticker_faces` (`personId`)")
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `people` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT)",
                )
            }
        }

        /** Adds the user's description and the picture tags they removed. */
        val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE stickers ADD COLUMN userDescription TEXT")
                db.execSQL("ALTER TABLE stickers ADD COLUMN removedImageTags TEXT")
            }
        }

        /** Adds [StickerEntity.learnedTags]: the user's tags suggested on look-alike stickers. */
        val MIGRATION_9_10 = object : Migration(9, 10) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE stickers ADD COLUMN learnedTags TEXT")
            }
        }

        /** Adds [SearchPick]: which stickers the user sent after which searches. */
        val MIGRATION_10_11 = object : Migration(10, 11) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS search_picks (queryKey TEXT NOT NULL, stickerId INTEGER NOT NULL, " +
                        "count INTEGER NOT NULL, lastAt INTEGER NOT NULL, PRIMARY KEY(queryKey, stickerId))",
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_search_picks_stickerId ON search_picks (stickerId)")
            }
        }

        /** Adds [SearchPick.tagged]: no existing pick has been turned into a tag yet. */
        val MIGRATION_11_12 = object : Migration(11, 12) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE search_picks ADD COLUMN tagged INTEGER NOT NULL DEFAULT 0")
            }
        }

        /**
         * Meaning vectors per facet (what a sticker says, what it shows). Existing vectors are kept
         * as facet 0 (combined) so search keeps working until each sticker is embedded again.
         */
        val MIGRATION_12_13 = object : Migration(12, 13) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `sticker_vectors_new` (`stickerId` INTEGER NOT NULL, `facet` INTEGER NOT NULL, " +
                        "`model` TEXT NOT NULL, `fingerprint` INTEGER NOT NULL, `vector` BLOB NOT NULL, " +
                        "PRIMARY KEY(`stickerId`, `facet`))",
                )
                db.execSQL(
                    "INSERT INTO sticker_vectors_new (stickerId, facet, model, fingerprint, vector) " +
                        "SELECT stickerId, 0, model, fingerprint, vector FROM sticker_vectors",
                )
                db.execSQL("DROP TABLE sticker_vectors")
                db.execSQL("ALTER TABLE sticker_vectors_new RENAME TO sticker_vectors")
            }
        }

        /** Adds what stickers are used for, learned from imported chats, and which chats those were. */
        val MIGRATION_13_14 = object : Migration(13, 14) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `sticker_contexts` (`stickerId` INTEGER NOT NULL, `model` TEXT NOT NULL, " +
                        "`uses` INTEGER NOT NULL, `vector` BLOB NOT NULL, PRIMARY KEY(`stickerId`))",
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `imported_chats` (`hash` TEXT NOT NULL, `importedAt` INTEGER NOT NULL, " +
                        "PRIMARY KEY(`hash`))",
                )
            }
        }
    }
}
