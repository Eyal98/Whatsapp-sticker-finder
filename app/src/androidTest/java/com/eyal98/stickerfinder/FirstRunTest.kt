package com.eyal98.stickerfinder

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.net.Uri
import android.os.Debug
import android.util.Log
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.eyal98.stickerfinder.data.StickerDatabase
import com.eyal98.stickerfinder.data.StickerEntity
import com.eyal98.stickerfinder.embed.LiteRtLmTextEmbedder
import com.eyal98.stickerfinder.index.StickerIndexer
import com.eyal98.stickerfinder.index.WorkBudget
import com.eyal98.stickerfinder.ml.BundledEmbedding
import com.eyal98.stickerfinder.ml.DeviceCapability
import com.eyal98.stickerfinder.ml.ModelCatalog
import com.eyal98.stickerfinder.ocr.TesseractTextReader
import com.eyal98.stickerfinder.search.TextEmbedder
import com.eyal98.stickerfinder.vision.SiglipImageEncoder
import com.eyal98.stickerfinder.vision.SiglipModel
import com.eyal98.stickerfinder.vision.StickerFaces
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.FileOutputStream

/**
 * First-run timings, peak memory, and the behaviour at each RAM band the product branches on.
 * Prints one `PERF key=value` line per measurement; CI collects them from logcat (see smoke-run.sh).
 * Fixtures are generated here, never read from a user's sticker folder.
 */
@RunWith(AndroidJUnit4::class)
class FirstRunTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val arguments = InstrumentationRegistry.getArguments()

    private enum class Band(val label: String) {
        BELOW_3G("below3"),
        BAND_3_TO_4G("3to4"),
        ABOVE_4G("above4"),
    }

    private fun bandOf(ram: Long) = when {
        ram < ModelCatalog.GRANITE_EMBEDDING.minRamBytes -> Band.BELOW_3G
        ram < LOW_MEMORY_BYTES -> Band.BAND_3_TO_4G
        else -> Band.ABOVE_4G
    }

    @Test
    fun ramIsInTheExpectedBand() {
        val ram = DeviceCapability.totalRamBytes(context)
        val band = bandOf(ram)
        perf("ram_total_bytes=$ram band=${band.label}")
        arguments.getString(EXPECT_BAND_ARG)?.let { assertEquals("RAM band of this emulator profile", it, band.label) }
    }

    @Test
    fun modelGatesMatchTheRamBand() {
        val ram = DeviceCapability.totalRamBytes(context)
        val embed = DeviceCapability.canRun(context, ModelCatalog.GRANITE_EMBEDDING, ModelCatalog.GRANITE_EMBEDDING)
        val tags = DeviceCapability.canRun(context, ModelCatalog.SIGLIP2_B16, ModelCatalog.SIGLIP2_B16)
        perf("gates ram_total_bytes=$ram semantic_search=$embed picture_tags=$tags")
        if (bandOf(ram) == Band.BELOW_3G) {
            assertFalse("semantic search must be off below 3 GB", embed)
            assertFalse("picture tags must be off below 3 GB", tags)
        } else {
            assertTrue("semantic search must be on at 3 GB and up", embed)
            assertTrue("picture tags must be on at 3 GB and up", tags)
        }
    }

    @Test
    fun firstModelLoadsAreTimed() {
        val ram = DeviceCapability.totalRamBytes(context)
        if (!DeviceCapability.canRun(context, ModelCatalog.SIGLIP2_B16, ModelCatalog.SIGLIP2_B16)) {
            perf("model_loads skipped=gated ram_total_bytes=$ram")
            return
        }
        val ocr = timed("ocr_load") { TesseractTextReader.create(context) }
        assertNotNull("OCR language files didn't load", ocr)
        ocr!!.close()

        val encoder = timed("picture_load") { SiglipImageEncoder(SiglipModel.map(context)) }
        encoder.use { timed("picture_first_encode") { it.encode(shapeBitmap()) } }

        val faces = timed("faces_load") { StickerFaces(StickerFaces.mapModel(context)) }
        faces.use { timed("faces_first_find") { it.find(shapeBitmap()) } }

        val status = timed("semantic_model_install") { BundledEmbedding.install(context) }
        assertEquals(BundledEmbedding.Status.READY, status)
        val model = BundledEmbedding.installed(context)
        assertNotNull(model)
        val embedder = timed("semantic_model_load") { LiteRtLmTextEmbedder.create(context, model!!) }
        try {
            timed("semantic_first_embed") { embedder.embed("running late", TextEmbedder.Kind.QUERY) }
        } finally {
            embedder.close()
        }
    }

    @Test
    fun indexingFinishesAndKeywordSearchWorksAtThisRam() {
        val run = indexRun(FIXTURE_SMALL)
        assertTrue("indexing $FIXTURE_SMALL fixtures did not finish", run.finished)
        assertTrue("keyword search found no fixture", run.keywordHits > 0)
    }

    @Test
    fun indexingALargeCorpusIsTimed() {
        val count = arguments.getString(LARGE_ARG)?.toInt() ?: FIXTURE_LARGE
        if (count > 0) indexRun(count)
    }

    private class IndexRun(val finished: Boolean, val keywordHits: Int)

    private fun indexRun(count: Int): IndexRun {
        val dir = File(context.cacheDir, "first-run-fixtures/$count").apply { deleteRecursively(); mkdirs() }
        val files = (0 until count).map { i ->
            File(dir, "sticker_$i.webp").also { file ->
                FileOutputStream(file).use { fixtureBitmap(i).compress(Bitmap.CompressFormat.WEBP_LOSSY, 80, it) }
            }
        }
        val name = "first-run-$count.db"
        context.deleteDatabase(name)
        val db = Room.databaseBuilder(context, StickerDatabase::class.java, name).build()
        try {
            return runBlocking {
                val dao = db.stickerDao()
                dao.insertAll(
                    files.map {
                        StickerEntity(
                            documentUri = Uri.fromFile(it).toString(),
                            displayName = it.name,
                            sizeBytes = it.length(),
                            lastModified = it.lastModified(),
                        )
                    },
                )
                val readers = createTextReaders()
                val peak = MemoryPeak().apply { start() }
                val started = System.nanoTime()
                val progress = try {
                    StickerIndexer(context.contentResolver, dao, readers).indexPending(WorkBudget(INDEX_BUDGET_MILLIS))
                } finally {
                    readers.forEach { it.close() }
                    peak.stop()
                }
                val millis = (System.nanoTime() - started) / 1_000_000
                val hits = dao.searchFts("hello", 50).size
                val perSticker = if (progress.processed > 0) millis / progress.processed else -1
                perf(
                    "index count=$count processed=${progress.processed} finished=${progress.finished} " +
                        "ms=$millis per_sticker_ms=$perSticker readers=${readers.size} keyword_hits=$hits " +
                        "peak_pss_kb=${peak.pssKb} peak_java_heap_kb=${peak.javaHeapKb} peak_native_kb=${peak.nativeHeapKb}",
                )
                IndexRun(progress.finished, hits)
            }
        } finally {
            db.close()
            context.deleteDatabase(name)
        }
    }

    /** Mirrors IndexWorker.createTextReaders, which is private to the worker. */
    private fun createTextReaders(): List<TesseractTextReader> {
        val byCores = (Runtime.getRuntime().availableProcessors() / 2).coerceIn(1, MAX_READERS)
        val lowMemory = DeviceCapability.totalRamBytes(context) < LOW_MEMORY_BYTES
        val count = if (lowMemory) byCores.coerceAtMost(2) else byCores
        val first = TesseractTextReader.create(context) ?: return emptyList()
        return listOf(first) + List(count - 1) { TesseractTextReader.create(context) }.filterNotNull()
    }

    /** Distinct pixels per index, so every fixture has its own content hash. */
    private fun fixtureBitmap(index: Int): Bitmap = textBitmap("HELLO").also { bitmap ->
        Canvas(bitmap).drawCircle(
            (index * 37 % 480 + 16).toFloat(),
            (index * 53 % 480 + 16).toFloat(),
            12f,
            Paint().apply { color = Color.BLUE },
        )
    }

    private inline fun <T> timed(label: String, block: () -> T): T {
        val started = System.nanoTime()
        return block().also { perf("$label ms=${(System.nanoTime() - started) / 1_000_000}") }
    }

    private fun perf(line: String) {
        Log.i(TAG, "PERF $line")
    }

    /** Samples PSS and heap on a background thread while indexing runs. */
    private class MemoryPeak {
        private var running = false
        private var sampler: Thread? = null
        var pssKb = 0
            private set
        var javaHeapKb = 0L
            private set
        var nativeHeapKb = 0L
            private set

        fun start() {
            running = true
            sampler = Thread {
                while (running) {
                    sample()
                    Thread.sleep(SAMPLE_MILLIS)
                }
            }.also { it.start() }
        }

        fun stop() {
            running = false
            sampler?.join()
            sample()
        }

        private fun sample() {
            val info = Debug.MemoryInfo()
            Debug.getMemoryInfo(info)
            pssKb = maxOf(pssKb, info.totalPss)
            val runtime = Runtime.getRuntime()
            javaHeapKb = maxOf(javaHeapKb, (runtime.totalMemory() - runtime.freeMemory()) / 1024)
            nativeHeapKb = maxOf(nativeHeapKb, Debug.getNativeHeapAllocatedSize() / 1024)
        }
    }

    private companion object {
        const val TAG = "PeelItPerf"
        const val EXPECT_BAND_ARG = "expectRamBand"
        const val LARGE_ARG = "perfLargeCount"
        const val FIXTURE_SMALL = 20
        const val FIXTURE_LARGE = 1000
        const val INDEX_BUDGET_MILLIS = 12 * 60 * 1000L
        const val SAMPLE_MILLIS = 100L
        const val MAX_READERS = 4

        /** IndexWorker.LOW_MEMORY_BYTES, which is private there. */
        const val LOW_MEMORY_BYTES = 4_000_000_000L
    }
}
