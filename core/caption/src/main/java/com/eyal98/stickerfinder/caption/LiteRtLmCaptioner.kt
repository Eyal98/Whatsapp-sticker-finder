package com.eyal98.stickerfinder.caption

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.util.Log
import androidx.core.content.edit
import com.eyal98.stickerfinder.ml.InstalledModel
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Content
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.SamplerConfig
import java.io.ByteArrayOutputStream

/**
 * Captions stickers with a multimodal Gemma model in the `.litertlm` format, through LiteRT-LM.
 * Runs entirely on the phone; the app has no network permission.
 */
class LiteRtLmCaptioner private constructor(
    private val engine: Engine,
    override val modelId: String,
    override val setupName: String,
    /** Called once, after the first caption: this model/backend setup works. */
    private var onFirstCaption: (() -> Unit)?,
) : StickerCaptioner {

    override fun caption(sticker: Bitmap, printedText: String?, packName: String?): StickerCaption? {
        val image = StickerImage.flatten(sticker)
        val png = try {
            ByteArrayOutputStream().use { out ->
                image.compress(Bitmap.CompressFormat.PNG, 100, out)
                out.toByteArray()
            }
        } finally {
            image.recycle()
        }
        // A fresh conversation per sticker, so one sticker's description can't leak into the next.
        val text = engine.createConversation(conversationConfig).use { conversation ->
            val reply = conversation.sendMessage(
                Contents.of(Content.ImageBytes(png), Content.Text(CaptionPrompt.build(printedText, packName))),
            )
            reply.contents.contents.filterIsInstance<Content.Text>().joinToString("") { it.text }
        }
        onFirstCaption?.invoke()
        onFirstCaption = null
        return CaptionPrompt.parse(text)
    }

    override fun close() {
        engine.close()
    }

    /** Where the text model and the image encoder run. */
    private class Setup(val name: String, val backend: () -> Backend, val vision: () -> Backend)

    companion object {
        private const val TAG = "LiteRtLmCaptioner"
        // Image, prompt and reply together; the reply asks for up to 20 keywords in two languages.
        private const val MAX_TOKENS = 2048
        private const val PREFS = "litertlm_caption"

        /**
         * Fastest first. The GPU reads the image and prompt several times faster than the CPU
         * (Google's Gemma 4 E2B figures: 3,808 vs 557 tokens/s prefill), and each sticker is a
         * few hundred image tokens.
         */
        private val SETUPS = listOf(
            Setup("gpu", { Backend.GPU() }, { Backend.GPU() }),
            Setup("cpu+gpu-vision", { Backend.CPU() }, { Backend.GPU() }),
            Setup("cpu", { Backend.CPU() }, { Backend.CPU() }),
        )

        // Low temperature: we want a consistent description, not creativity.
        private val conversationConfig = ConversationConfig(
            samplerConfig = SamplerConfig(topK = 10, topP = 0.95, temperature = 0.2, seed = 0),
        )

        /**
         * Loads the model, trying [SETUPS] in order. Slow (seconds) and memory-hungry: call from
         * a background worker.
         *
         * A setup that crashes the app natively can't be caught, so each attempt is recorded
         * before it starts and confirmed after its first caption. A setup that was started but
         * never produced a caption is skipped next time, so a GPU driver crash costs one run, not
         * every run.
         */
        fun create(context: Context, model: InstalledModel): LiteRtLmCaptioner {
            val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            val key = model.sha256.take(16)
            val first = firstSetupToTry(prefs, key)
            var failure: Throwable? = null
            for (index in first until SETUPS.size) {
                val setup = SETUPS[index]
                prefs.edit(commit = true) { putInt("trying_$key", index) }
                var engine: Engine? = null
                try {
                    engine = Engine(
                        EngineConfig(
                            modelPath = model.file.absolutePath,
                            backend = setup.backend(),
                            visionBackend = setup.vision(),
                            maxNumTokens = MAX_TOKENS,
                            maxNumImages = 1,
                            cacheDir = context.cacheDir.absolutePath,
                        ),
                    )
                    engine.initialize()
                    Log.i(TAG, "Loaded ${model.displayName} with ${setup.name}")
                    val confirmed = prefs.getInt("ok_$key", -1) == index
                    return LiteRtLmCaptioner(
                        engine,
                        model.id,
                        "litert-lm ${setup.name}",
                        if (confirmed) null else ({ prefs.edit { putInt("ok_$key", index) } }),
                    )
                } catch (e: Exception) {
                    Log.w(TAG, "${setup.name} failed", e)
                    failure = recordFailure(engine, e, failure)
                } catch (e: LinkageError) {
                    // The native library didn't load; the other setups would fail the same way.
                    throw IllegalStateException("LiteRT-LM native library unavailable", recordFailure(engine, e, failure))
                }
            }
            // Every setup failed to load: start from the fastest again next time (e.g. after an
            // update), since these were ordinary errors rather than crashes.
            prefs.edit { remove("trying_$key") }
            throw IllegalStateException("Could not load ${model.displayName}", failure)
        }

        /** The confirmed setup, or the one after a setup that was tried but never confirmed. */
        private fun firstSetupToTry(prefs: SharedPreferences, key: String): Int {
            val ok = prefs.getInt("ok_$key", -1)
            val trying = prefs.getInt("trying_$key", -1)
            return when {
                trying < 0 -> maxOf(ok, 0)
                trying == ok -> ok
                // The last attempt never captioned anything (crash, or stopped very early).
                else -> minOf(trying + 1, SETUPS.lastIndex)
            }
        }

        /**
         * Closing an engine that didn't initialize can throw too; that mustn't skip the next
         * setup or hide the real error. Returns [error] with earlier failures attached.
         */
        private fun recordFailure(engine: Engine?, error: Throwable, earlier: Throwable?): Throwable {
            runCatching { engine?.close() }
            earlier?.let(error::addSuppressed)
            return error
        }
    }
}
