package com.eyal98.stickerfinder.data

import com.eyal98.stickerfinder.search.PackedVectors
import com.eyal98.stickerfinder.search.TextEmbedder
import com.eyal98.stickerfinder.search.Vectors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** Runs [block] with the loaded embedder, or returns null when no embedding model is installed. */
interface EmbedderAccess {
    suspend fun <T> withEmbedder(block: (TextEmbedder) -> T): T?
}

/**
 * Finds stickers whose meaning is close to the query, in either language. Vectors are cached in
 * memory, compactly (see [PackedVectors]), and reloaded only when the table changes or after
 * [release].
 */
class SemanticSearch(
    private val dao: StickerDao,
    private val embedders: EmbedderAccess,
    private val minSimilarity: () -> Float = { SearchSettings.DEFAULT_MIN_SIMILARITY },
) {
    private val lock = Mutex()
    private var cached: Triple<String, VectorSignature, PackedVectors>? = null

    /**
     * Recent queries' vectors: typing back and forth ("cat", "cats", "cat") and the keyboard
     * repeating the app's search shouldn't run the model again. Access-ordered, so it's an LRU.
     */
    private val queryCache = object : LinkedHashMap<String, Pair<String, FloatArray>>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Pair<String, FloatArray>>) =
            size > QUERY_CACHE_SIZE
    }

    /** Sticker ids ordered by similarity; empty when semantic search isn't available. */
    suspend fun search(query: String, limit: Int = LIMIT): List<Long> =
        searchScored(query, limit, minSimilarity()).map { it.first }

    /** Sticker ids with their similarity, best first, keeping those at least [minSimilarity]. */
    suspend fun searchScored(query: String, limit: Int, minSimilarity: Float): List<Pair<Long, Float>> {
        val key = query.trim()
        val cachedQuery = synchronized(queryCache) { queryCache[key] }
        val result = cachedQuery ?: embedders.withEmbedder { embedder ->
            embedder.modelId to Vectors.prepare(embedder.embed(key, TextEmbedder.Kind.QUERY), embedder.dimensions)
        }?.also { synchronized(queryCache) { queryCache[key] = it } } ?: return emptyList()
        val (model, queryVector) = result
        val index = index(model)
        if (index.size == 0) {
            // Possibly a vector from a model that has since been replaced.
            if (cachedQuery != null) synchronized(queryCache) { queryCache.clear() }
            return emptyList()
        }
        return withContext(Dispatchers.Default) { index.search(queryVector, limit, minSimilarity) }
    }

    private suspend fun index(model: String): PackedVectors = lock.withLock {
        val signature = dao.vectorSignature()
        cached?.let { (m, s, index) -> if (m == model && s == signature) return index }
        // Let the old index go before loading the new one, rather than holding both.
        cached = null
        val index = load(model, signature.count)
        cached = Triple(model, signature, index)
        index
    }

    /** Reads [model]'s vectors a page at a time: all the stored bytes at once are tens of MB. */
    private suspend fun load(model: String, expected: Int): PackedVectors = withContext(Dispatchers.Default) {
        var index: PackedVectors? = null
        var after = -1L
        while (true) {
            val page = dao.meaningVectorPage(model, after, PAGE)
            if (page.isEmpty()) break
            for (row in page) {
                val packed = index ?: PackedVectors(row.vector.size / 4, expected).also { index = it }
                packed.addEncoded(row.stickerId, row.vector)
            }
            after = page.last().stickerId
        }
        index?.also { it.trim() } ?: PackedVectors(Vectors.DIMENSIONS, 1)
    }

    /** Frees the cached vectors (the app went to the background); the next search reloads them. */
    suspend fun release() = lock.withLock { cached = null }

    companion object {
        const val LIMIT = 100
        private const val PAGE = 500
        private const val QUERY_CACHE_SIZE = 64
    }
}
