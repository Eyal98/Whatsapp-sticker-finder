package com.eyal98.stickerfinder.caption

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.genai.llminference.GraphOptions
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import com.google.mediapipe.tasks.genai.llminference.LlmInferenceSession

/**
 * Captions stickers with a multimodal Gemma model through MediaPipe's LLM Inference API. Runs
 * entirely on the phone; the app has no network permission.
 */
class MediaPipeCaptioner private constructor(
    private val llm: LlmInference,
    override val modelId: String,
) : StickerCaptioner {

    override fun caption(sticker: Bitmap, printedText: String?): StickerCaption? {
        val image = flatten(sticker)
        try {
            // A fresh session per sticker, so one sticker's description can't leak into the next.
            val session = LlmInferenceSession.createFromOptions(llm, sessionOptions)
            try {
                session.addQueryChunk(CaptionPrompt.build(printedText))
                session.addImage(BitmapImageBuilder(image).build())
                return CaptionPrompt.parse(session.generateResponse())
            } finally {
                session.close()
            }
        } finally {
            image.recycle()
        }
    }

    override fun close() {
        llm.close()
    }

    companion object {
        private const val MAX_TOKENS = 1024

        /**
         * Stickers are transparent. On white, white text disappears; on black, black text does.
         * A mid-gray background keeps both readable for the model.
         */
        private const val BACKGROUND = 0xFF9E9E9E.toInt()

        private val sessionOptions: LlmInferenceSession.LlmInferenceSessionOptions =
            LlmInferenceSession.LlmInferenceSessionOptions.builder()
                // Low temperature: we want a consistent description, not creativity.
                .setTemperature(0.2f)
                .setTopK(10)
                .setGraphOptions(GraphOptions.builder().setEnableVisionModality(true).build())
                .build()

        /** Loads the model. Slow (seconds) and memory-hungry: call from a background worker. */
        fun create(context: Context, model: InstalledModel): MediaPipeCaptioner {
            val options = LlmInference.LlmInferenceOptions.builder()
                .setModelPath(model.file.absolutePath)
                .setMaxTokens(MAX_TOKENS)
                .setMaxNumImages(1)
                .build()
            return MediaPipeCaptioner(LlmInference.createFromOptions(context, options), model.id)
        }

        private fun flatten(sticker: Bitmap): Bitmap {
            val flat = Bitmap.createBitmap(sticker.width, sticker.height, Bitmap.Config.ARGB_8888)
            Canvas(flat).apply {
                drawColor(BACKGROUND)
                drawBitmap(sticker, 0f, 0f, null)
            }
            return flat
        }
    }
}
