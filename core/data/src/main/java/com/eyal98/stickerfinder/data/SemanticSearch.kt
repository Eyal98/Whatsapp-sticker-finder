package com.eyal98.stickerfinder.data

import com.eyal98.stickerfinder.search.MeaningIndex
import com.eyal98.stickerfinder.search.MeaningSelection
import com.eyal98.stickerfinder.search.TextEmbedder
import com.eyal98.stickerfinder.search.Vectors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** Runs [block] with the loaded embedder, or returns null when no embedding model is installed. */
interface EmbedderAccess {
    suspend fun <T> withEmbedder(block: (TextEmbedder) -> T): T?

    /**
     * What identifies the installed model file (it changes when the user installs another), or
     * null when there's none; cheap, without loading the model.
     */
    fun activeModelKey(): String? = null
}

/**
 * Finds stickers whose meaning is close to the query, in either language. Each sticker has up to
 * two vectors (what it says, what it shows), plus one for what it's used for if it was learned
 * from the user's chats, and matches by the closest one; only stickers that
 * clearly stand out for this query count (see [MeaningSelection]). Vectors are held compactly in
 * memory ([MeaningIndex]) and reloaded only when the table changes.
 */
class SemanticSearch(
    private val dao: StickerDao,
    private val embedders: EmbedderAccess,
    private val minSimilarity: () -> Float = { SearchSettings.DEFAULT_MIN_SIMILARITY },
) {
    private val lock = Mutex()
    private class Cached(
        val model: String,
        val signature: VectorSignature,
        val contexts: VectorSignature,
        val index: MeaningIndex,
        val builtAt: Long,
    )

    private var cached: Cached? = null

    /**
     * Recent queries' vectors: typing back and forth ("cat", "cats", "cat") and the keyboard
     * repeating the app's search shouldn't run the model again. Access-ordered, so it's an LRU.
     */
    private val queryCache = object : LinkedHashMap<String, Pair<String, FloatArray>>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Pair<String, FloatArray>>) =
            size > QUERY_CACHE_SIZE
    }

    /** Sticker ids ordered by similarity; empty when semantic search isn't available. */
    suspend fun search(query: String, limit: Int = LIMIT, scope: Set<Long>? = null): List<Long> =
        searchScored(query, limit, minSimilarity(), scope = scope).map { it.first }

    /**
     * Sticker ids with their similarity, best first: those at least [minSimilarity] similar that
     * stand out from the rest by [minZ] (see [MeaningSelection]).
     */
    suspend fun searchScored(
        query: String,
        limit: Int,
        minSimilarity: Float,
        minZ: Float = MeaningSelection.MIN_Z,
        /** Only these stickers (a folder), chosen among before the cut-off and limits. */
        scope: Set<Long>? = null,
    ): List<Pair<Long, Float>> {
        val text = query.trim()
        // By model file too: a query's vector from a model since replaced must not be used, even
        // while that model's old sticker vectors are still in the table.
        val modelKey = embedders.activeModelKey()
        val key = modelKey?.let { "$it\u0000$text" }
        val cachedQuery = key?.let { synchronized(queryCache) { queryCache[it] } }
        val result = cachedQuery ?: embedders.withEmbedder { embedder ->
            embedder.modelId to Vectors.prepare(embedder.embed(text, TextEmbedder.Kind.QUERY), embedder.dimensions)
        }?.also { v -> key?.let { synchronized(queryCache) { queryCache[it] = v } } } ?: return emptyList()
        val (model, queryVector) = result
        val index = index(model, queryVector.size)
        if (index.size == 0) return emptyList()
        return withContext(Dispatchers.Default) {
            val scores = index.scores(queryVector).let { all -> if (scope == null) all else all.filter { it.stickerId in scope } }
            MeaningSelection.select(scores, minSimilarity, limit, minZ)
        }
    }

    private suspend fun index(model: String, dims: Int): MeaningIndex = lock.withLock {
        val signature = dao.vectorSignature(model)
        val contexts = dao.contextSignature(model)
        val now = System.currentTimeMillis()
        cached?.let { c ->
            if (c.model == model && c.signature == signature && c.contexts == contexts) return c.index
            // While vectors are being (re)computed the table changes every few seconds; reloading
            // them all each time made searching stutter. A slightly stale index is fine meanwhile,
            // but not after a chat import: the user is about to try what it learned.
            if (c.model == model && c.contexts == contexts && now - c.builtAt < MIN_REBUILD_MILLIS) return c.index
        }
        // Drop the old index first: holding both at once doubles the memory for a moment.
        cached = null
        val builder = MeaningIndex.Builder(dims)
        val packs = HashMap<String, Int>()
        var afterId = -1L
        var afterFacet = -1
        withContext(Dispatchers.Default) {
            while (true) {
                val page = dao.meaningIndexPage(model, afterId, afterFacet, PAGE)
                if (page.isEmpty()) break
                for (row in page) {
                    val pack = row.packName?.let { name -> packs.getOrPut(name) { packs.size } } ?: -1
                    builder.add(row.stickerId, Vectors.decode(row.vector), pack)
                }
                afterId = page.last().stickerId
                afterFacet = page.last().facet
            }
            // What stickers are used for, learned from chats: one more vector each, favored a little
            // since it reflects how the user actually uses the sticker.
            var afterContext = -1L
            while (true) {
                val page = dao.contextIndexPage(model, afterContext, PAGE)
                if (page.isEmpty()) break
                for (row in page) {
                    val pack = row.packName?.let { name -> packs.getOrPut(name) { packs.size } } ?: -1
                    builder.add(row.stickerId, Vectors.prepare(Vectors.decode(row.vector), dims), pack, CONTEXT_WEIGHT)
                }
                afterContext = page.last().stickerId
            }
        }
        val index = builder.build()
        cached = Cached(model, signature, contexts, index, now)
        index
    }

    companion object {
        const val LIMIT = 100
        private const val QUERY_CACHE_SIZE = 64
        private const val PAGE = 500
        private const val MIN_REBUILD_MILLIS = 30_000L

        /** How much a context vector's similarity counts, relative to the sticker's own vectors. */
        private const val CONTEXT_WEIGHT = 1.05f
    }
}
