package com.eyal98.stickerfinder.index

import android.content.Context
import androidx.core.content.edit
import com.eyal98.stickerfinder.data.ImageTagFilter
import com.eyal98.stickerfinder.data.StickerDao
import com.eyal98.stickerfinder.data.UserTags
import com.eyal98.stickerfinder.ml.ModelCatalog
import com.eyal98.stickerfinder.search.LearnedTags
import com.eyal98.stickerfinder.search.Vectors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale

/**
 * Suggests the user's own tags on stickers that look like the ones that have them (see
 * [LearnedTags]), from the stored picture vectors: no model runs. A second or two for thousands
 * of stickers, and skipped when neither the tags nor the pictures changed since the last run.
 */
class LearnedTagger(private val context: Context, private val dao: StickerDao) {

    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** Brings learned tags up to date. Returns the number of stickers whose learned tags changed. */
    suspend fun refresh(): Int = withContext(Dispatchers.Default) {
        val states = dao.tagStates()
        val vectorCount = dao.imageVectorCount(MODEL)
        val signature = signature(states.map { Triple(it.id, it.userTags, it.removedImageTags) }, vectorCount)
        if (prefs.getLong(KEY_SIGNATURE, 0L) == signature) return@withContext 0

        val vectors = HashMap<Long, FloatArray>(vectorCount * 2)
        var after = -1L
        while (true) {
            val page = dao.imageVectorPage(MODEL, after, PAGE)
            if (page.isEmpty()) break
            page.forEach { vectors[it.stickerId] = Vectors.decode(it.vector) }
            after = page.last().stickerId
        }

        val tags = states.associate { it.id to UserTags.parse(it.userTags) }.filterValues { it.isNotEmpty() }
        val blocked = states.associate { s ->
            s.id to ImageTagFilter.split(s.removedImageTags).map { it.lowercase() }.toSet()
        }.filterValues { it.isNotEmpty() }
        val result = if (vectors.isEmpty() || tags.isEmpty()) null else LearnedTags.suggest(vectors, tags, blocked)

        var changed = 0
        for (s in states) {
            val own = UserTags.parse(s.userTags).map { it.lowercase() }.toSet()
            val learned = result?.suggestions?.get(s.id).orEmpty()
                .map { it.tag }
                .filter { it.lowercase() !in own }
                .joinToString(", ")
                .ifEmpty { null }
            if (learned != s.learnedTags) {
                dao.setLearnedTags(s.id, learned)
                changed++
            }
        }
        prefs.edit {
            putLong(KEY_SIGNATURE, signature)
            result?.stats?.let { st ->
                putString(
                    KEY_STATS,
                    String.format(
                        Locale.US,
                        "learned tags: %d of %d tags spread to %d stickers; random picture pairs: median %.2f, 99%% %.2f",
                        st.learnedTags, st.tags, result.suggestions.size, st.randomMedian, st.randomP99,
                    ),
                )
            }
        }
        changed
    }

    private fun signature(states: List<Triple<Long, String, String?>>, vectors: Int): Long {
        var h = -0x340d631b7bdddcdbL + VERSION
        fun mix(x: Long) { h = (h xor x) * 0x100000001b3L }
        mix(vectors.toLong())
        for ((id, tags, removed) in states) {
            mix(id)
            mix(tags.hashCode().toLong())
            mix(removed.hashCode().toLong())
        }
        return h
    }

    companion object {
        /** The picture model whose vectors are compared. */
        private val MODEL = ModelCatalog.SIGLIP2_B16.id
        private const val PAGE = 500
        private const val PREFS = "learned_tags"
        private const val KEY_SIGNATURE = "signature"
        private const val KEY_STATS = "stats"

        /** Bump when the rules in [LearnedTags] change, so every sticker is recomputed. */
        private const val VERSION = 1

        /** One line for diagnostics (numbers only), or null before the first run. */
        fun stats(context: Context): String? =
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_STATS, null)
    }
}
