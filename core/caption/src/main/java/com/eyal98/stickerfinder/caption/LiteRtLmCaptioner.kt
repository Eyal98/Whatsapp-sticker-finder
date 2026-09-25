package com.eyal98.stickerfinder.caption

import android.content.Context
import android.graphics.Bitmap
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
) : StickerCaptioner {

    override fun caption(sticker: Bitmap, printedText: String?): StickerCaption? {
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
        engine.createConversation(conversationConfig).use { conversation ->
            val reply = conversation.sendMessage(
                Contents.of(Content.ImageBytes(png), Content.Text(CaptionPrompt.build(printedText))),
            )
            val text = reply.contents.contents.filterIsInstance<Content.Text>().joinToString("") { it.text }
            return CaptionPrompt.parse(text)
        }
    }

    override fun close() {
        engine.close()
    }

    companion object {
        private const val MAX_TOKENS = 1024

        // Low temperature: we want a consistent description, not creativity.
        private val conversationConfig = ConversationConfig(
            samplerConfig = SamplerConfig(topK = 10, topP = 0.95, temperature = 0.2, seed = 0),
        )

        /**
         * Loads the model. Slow (seconds) and memory-hungry: call from a background worker.
         * Tries the GPU for the image encoder first, as Google's samples do, then the CPU.
         */
        fun create(context: Context, model: InstalledModel): LiteRtLmCaptioner {
            var failure: Throwable? = null
            for (vision in listOf<() -> Backend>({ Backend.GPU() }, { Backend.CPU() })) {
                var engine: Engine? = null
                try {
                    engine = Engine(
                        EngineConfig(
                            modelPath = model.file.absolutePath,
                            backend = Backend.CPU(),
                            visionBackend = vision(),
                            maxNumTokens = MAX_TOKENS,
                            maxNumImages = 1,
                            cacheDir = context.cacheDir.absolutePath,
                        ),
                    )
                    engine.initialize()
                    return LiteRtLmCaptioner(engine, model.id)
                } catch (e: Exception) {
                    failure = recordFailure(engine, e, failure)
                } catch (e: LinkageError) {
                    // The native library didn't load; the CPU attempt would fail the same way.
                    throw IllegalStateException("LiteRT-LM native library unavailable", recordFailure(engine, e, failure))
                }
            }
            throw IllegalStateException("Could not load ${model.displayName}", failure)
        }

        /**
         * Closing an engine that didn't initialize can throw too; that mustn't skip the CPU
         * attempt or hide the real error. Returns [error] with earlier failures attached.
         */
        private fun recordFailure(engine: Engine?, error: Throwable, earlier: Throwable?): Throwable {
            runCatching { engine?.close() }
            earlier?.let(error::addSuppressed)
            return error
        }
    }
}
