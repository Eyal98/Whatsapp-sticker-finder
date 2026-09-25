package com.eyal98.stickerfinder.search

import java.io.Closeable

/** Turns text into a vector whose direction captures its meaning, in any supported language. */
interface TextEmbedder : Closeable {

    /** Identifies the model; vectors from different models can't be compared. */
    val modelId: String

    enum class Kind {
        /** What the user typed in the search box. */
        QUERY,

        /** A sticker's description, printed text and tags. */
        DOCUMENT,
    }

    fun embed(text: String, kind: Kind): FloatArray
}
