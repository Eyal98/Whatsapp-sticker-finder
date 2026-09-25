package com.eyal98.stickerfinder.data

import android.content.Context
import androidx.core.content.edit

/** Search tuning the user can change from the quality test. */
class SearchSettings(context: Context) {

    private val prefs = context.applicationContext.getSharedPreferences("search_settings", Context.MODE_PRIVATE)

    /** Semantic results below this cosine similarity are treated as unrelated. */
    var minSimilarity: Float
        get() = prefs.getFloat(KEY_MIN_SIMILARITY, DEFAULT_MIN_SIMILARITY)
        set(value) = prefs.edit { putFloat(KEY_MIN_SIMILARITY, value) }

    companion object {
        private const val KEY_MIN_SIMILARITY = "min_similarity"

        /** A first guess for EmbeddingGemma at 256 dimensions, until the quality test tunes it. */
        const val DEFAULT_MIN_SIMILARITY = 0.3f
    }
}
