package com.eyal98.stickerfinder

import android.content.Context
import android.util.Log
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.eyal98.stickerfinder.data.EmbedderAccess
import com.eyal98.stickerfinder.data.IndexVersion
import com.eyal98.stickerfinder.data.SemanticSearch
import com.eyal98.stickerfinder.data.StickerDatabase
import com.eyal98.stickerfinder.data.StickerEntity
import com.eyal98.stickerfinder.data.StickerVector
import com.eyal98.stickerfinder.data.UserTags
import com.eyal98.stickerfinder.index.EmbedIndexer
import com.eyal98.stickerfinder.index.LearnedTagger
import com.eyal98.stickerfinder.ml.ModelCatalog
import com.eyal98.stickerfinder.search.EmbeddingText
import com.eyal98.stickerfinder.search.TextEmbedder
import com.eyal98.stickerfinder.search.Vectors
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import kotlin.concurrent.thread
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * How much Java heap the passes that read every sticker's vectors take, on a collection the size
 * of a tester's that ran out of memory (10,308 stickers, 10,206 meaning vectors, picture vectors
 * for all, 768 floats each). Each pass runs on its own after a GC; the report goes to the log
 * (tag MemoryBudget), which the smoke run prints. Numbers are heap in use, so a peak includes
 * garbage not yet collected.
 */
@RunWith(AndroidJUnit4::class)
class MemoryBudgetTest {

    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var database: StickerDatabase
    private val report = mutableListOf<String>()

    private class FakeEmbedder : TextEmbedder {
        override val modelId = MEANING_MODEL
        override val dimensions = DIMS
        override fun embed(text: String, kind: TextEmbedder.Kind) = unit(Random(text.hashCode()))
        override fun close() {}
    }

    private val embedders = object : EmbedderAccess {
        private val embedder = FakeEmbedder()
        override suspend fun <T> withEmbedder(block: (TextEmbedder) -> T): T? = block(embedder)
    }

    @Before
    fun seed() {
        database = Room.inMemoryDatabaseBuilder(context, StickerDatabase::class.java).build()
        runBlocking { seedStickers() }
        seedVectors()
    }

    @After
    fun close() {
        database.close()
    }

    @Test
    fun vectorPassesStayWithinBudget() {
        val dao = database.stickerDao()
        report += "heap limit ${mb(Runtime.getRuntime().maxMemory())} MB"
        val semantic = SemanticSearch(dao, embedders) { 0.2f }
        measure("meaning search, first") { semantic.search("חתול") }
        // One vector changes (as while meaning vectors are updated): the index is built again.
        measure("meaning search, rebuilt") {
            dao.upsertVector(StickerVector(1, MEANING_MODEL, 1, Vectors.encode(unit(Random(1)))))
            semantic.search("cat")
        }
        measure("meaning vectors check") { EmbedIndexer(dao, embedders).embedPending() }
        context.getSharedPreferences("learned_tags", Context.MODE_PRIVATE).edit().clear().commit()
        measure("learned tags") { LearnedTagger(context, dao).refresh() }
        // Still referenced here, so its index counts as retained above.
        semantic.hashCode()
        Log.w(TAG, report.joinToString("\n"))
    }

    /** Runs [block] after a GC; records the highest heap use during it, and what it left behind. */
    private fun measure(name: String, block: suspend () -> Unit) {
        val before = settle()
        val running = AtomicBoolean(true)
        val peak = AtomicLong(before)
        val sampler = thread(isDaemon = true) {
            while (running.get()) {
                peak.accumulateAndGet(used()) { a, b -> maxOf(a, b) }
                Thread.sleep(1)
            }
        }
        val start = System.nanoTime()
        runBlocking { block() }
        val millis = (System.nanoTime() - start) / 1_000_000
        running.set(false)
        sampler.join()
        peak.accumulateAndGet(used()) { a, b -> maxOf(a, b) }
        val after = settle()
        report += String.format(
            Locale.US, "%-24s peak +%d MB, retained +%d MB, %d ms", name, mb(peak.get() - before), mb(after - before), millis,
        )
    }

    private suspend fun seedStickers() {
        val stickers = (1..STICKERS).map { i -> sticker(i) }
        stickers.chunked(500).forEach { database.stickerDao().insertAll(it) }
    }

    /** Meaning vectors for all but 1 in 100 (up to date with each sticker's text), picture vectors for all. */
    private fun seedVectors() {
        val random = Random(3)
        // Stickers of one "character" look alike, so the user's tags on a few spread to the rest.
        val characters = List(CHARACTERS) { unit(random) }
        val db = database.openHelper.writableDatabase
        db.beginTransaction()
        try {
            val meaning = db.compileStatement("INSERT INTO sticker_vectors (stickerId, model, fingerprint, vector) VALUES (?, ?, ?, ?)")
            val picture = db.compileStatement("INSERT INTO sticker_image_vectors (stickerId, model, vector) VALUES (?, ?, ?)")
            for (i in 1..STICKERS) {
                val s = sticker(i)
                if (i % 100 != 0) {
                    val text = EmbeddingText.document(
                        s.captionEn, s.captionHe, s.captionTags, s.ocrText, s.userTags, s.visibleImageTags,
                        s.packName, s.emojiWords, s.peopleNames, s.userDescription,
                    )!!
                    meaning.clearBindings()
                    meaning.bindLong(1, i.toLong())
                    meaning.bindString(2, MEANING_MODEL)
                    meaning.bindLong(3, EmbeddingText.fingerprint(text))
                    meaning.bindBlob(4, Vectors.encode(unit(random)))
                    meaning.executeInsert()
                }
                val center = characters[i % CHARACTERS]
                val look = FloatArray(DIMS) { center[it] + (random.nextFloat() * 2 - 1) * 0.05f }
                picture.clearBindings()
                picture.bindLong(1, i.toLong())
                picture.bindString(2, ModelCatalog.SIGLIP2_B16.id)
                picture.bindBlob(3, Vectors.encode(Vectors.prepare(look, DIMS)))
                picture.executeInsert()
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    /** A sticker with fields about as long as real ones (a WhatsApp document URI, both languages). */
    private fun sticker(i: Int): StickerEntity {
        val file = String.format(Locale.US, "STK-20250101-WA%05d.webp", i)
        return StickerEntity(
            id = i.toLong(),
            documentUri = URI_PREFIX + file,
            displayName = file,
            sizeBytes = 30_000,
            lastModified = 1_700_000_000_000L + i,
            perceptualHash = i * 0x9E3779B97F4A7C15uL.toLong(),
            ocrText = if (i % 4 == 0) "בוקר טוב good morning $i" else null,
            imageTags = "cat, cute, happy, חתול, חמוד, שמח",
            imageTaggedAt = 1L,
            imageTagsVersion = "v1",
            packName = "Pack ${i / 30}",
            packPublisher = "Publisher",
            emojiWords = "smile laugh heart חיוך צחוק לב",
            userTags = if (i % 25 == 0) UserTags.format(listOf("character ${i % CHARACTERS}")) else "",
            indexedAt = 1L,
            indexVersion = IndexVersion.CURRENT,
            facesScannedAt = 1L,
        )
    }

    private companion object {
        const val TAG = "MemoryBudget"
        const val STICKERS = 10_308
        const val DIMS = 768
        const val CHARACTERS = 40
        const val MEANING_MODEL = "memory-test-embedding"
        const val URI_PREFIX = "content://com.android.externalstorage.documents/tree/primary%3AAndroid%2Fmedia%2F" +
            "com.whatsapp%2FWhatsApp%2FMedia%2FWhatsApp%20Stickers/document/primary%3AAndroid%2Fmedia%2F" +
            "com.whatsapp%2FWhatsApp%2FMedia%2FWhatsApp%20Stickers%2F"

        fun unit(random: Random): FloatArray {
            val v = FloatArray(DIMS) { random.nextFloat() * 2 - 1 }
            val length = sqrt(v.sumOf { (it * it).toDouble() }).toFloat()
            for (i in v.indices) v[i] /= length
            return v
        }

        fun used(): Long = Runtime.getRuntime().let { it.totalMemory() - it.freeMemory() }

        fun settle(): Long {
            repeat(3) {
                Runtime.getRuntime().gc()
                System.runFinalization()
                Thread.sleep(100)
            }
            return used()
        }

        fun mb(bytes: Long) = bytes / (1024 * 1024)
    }
}
