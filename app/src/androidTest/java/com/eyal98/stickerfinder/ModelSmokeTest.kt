package com.eyal98.stickerfinder

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.eyal98.stickerfinder.embed.LiteRtLmTextEmbedder
import com.eyal98.stickerfinder.ml.BundledEmbedding
import com.eyal98.stickerfinder.ocr.TesseractTextReader
import com.eyal98.stickerfinder.search.TextEmbedder
import com.eyal98.stickerfinder.search.Vectors
import com.eyal98.stickerfinder.vision.PictureLabels
import com.eyal98.stickerfinder.vision.SiglipImageEncoder
import com.eyal98.stickerfinder.vision.SiglipModel
import com.eyal98.stickerfinder.vision.StickerFaces
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Runs every bundled model once inside the real app: OCR, picture tags, face detection and
 * embedding, and meaning search. Their native code looks up Java classes by name, so this is what
 * catches a build (or a minified build) that compiles but crashes on the phone. Run on an emulator
 * in CI (.github/workflows/smoke.yml).
 */
@RunWith(AndroidJUnit4::class)
class ModelSmokeTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    private fun norm(v: FloatArray) = sqrt(v.sumOf { (it * it).toDouble() }).toFloat()

    @Test
    fun ocrReadsPrintedText() {
        val reader = TesseractTextReader.create(context)
        assertNotNull("OCR language files didn't load", reader)
        reader!!.use {
            val text = it.read(textBitmap("HELLO")).orEmpty()
            assertTrue("OCR read \"$text\"", text.contains("HELLO", ignoreCase = true))
        }
    }

    @Test
    fun pictureModelEncodesAndTags() {
        assertTrue("picture model not bundled", SiglipModel.isBundled(context))
        val labels = PictureLabels.load(context)
        SiglipImageEncoder(SiglipModel.map(context)).use { encoder ->
            val vector = encoder.encode(shapeBitmap())
            assertTrue("empty picture vector", vector.isNotEmpty())
            assertTrue("vector length ${norm(vector)}", abs(norm(vector) - 1f) < 0.01f)
            // Must not throw; which labels fit a red disc isn't the point here.
            labels.tagsFor(vector)
        }
    }

    @Test
    fun faceModelsLoadAndRun() {
        assertTrue("face models not bundled", StickerFaces.isBundled(context))
        StickerFaces(StickerFaces.mapModel(context)).use { faces ->
            // No face on a plain shape: this checks ML Kit and SFace load and run without crashing.
            assertEquals(0, faces.find(shapeBitmap()).size)
        }
    }

    @Test
    fun meaningSearchFindsTheSameMeaningAcrossLanguages() {
        assertEquals(BundledEmbedding.Status.READY, BundledEmbedding.install(context))
        val model = BundledEmbedding.installed(context)
        assertNotNull(model)
        val embedder = LiteRtLmTextEmbedder.create(context, model!!)
        try {
            fun embed(text: String) = Vectors.prepare(embedder.embed(text, TextEmbedder.Kind.QUERY), embedder.dimensions)
            val late = embed("running late")
            val lateHebrew = embed("מאחר")
            val banana = embed("banana")
            val same = Vectors.dot(late, lateHebrew)
            val different = Vectors.dot(late, banana)
            assertTrue("running late ~ מאחר $same, ~ banana $different", same > different)
        } finally {
            embedder.close()
        }
    }
}
