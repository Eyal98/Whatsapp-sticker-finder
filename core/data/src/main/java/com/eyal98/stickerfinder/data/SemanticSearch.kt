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
}

/**
 * Finds stickers whose meaning is close to the query, in either language. Each sticker has up to
 * two vectors (what it says, what it shows) and matches by the closer one; only stickers that
 * clearly stand out for this query count (see [MeaningSelection]). Vectors are held compactly in
 * memory ([MeaningIndex]) and reloaded only when the table changes.
 */
class SemanticSearch(
    private val dao: StickerDao,
    private val embedders: EmbedderAccess,
    private val minSimilarity: () -> Float = { SearchSettings.DEFAULT_MIN_SIMILARITY },
) {
    private val lock = Mutex()
    private var cached: Triple<String, VectorSignature, MeaningIndex>? = null

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

    /**
     * Sticker ids with their similarity, best first: those at least [minSimilarity] similar that
     * stand out from the rest by [minZ] (see [MeaningSelection]).
     */
    suspend fun searchScored(
        query: String,
        limit: Int,
        minSimilarity: Float,
        minZ: Float = MeaningSelection.MIN_Z,
    ): List<Pair<Long, Float>> {
        val key = query.trim()
        val cachedQuery = synchronized(queryCache) { queryCache[key] }
        val result = cachedQuery ?: embedders.withEmbedder { embedder ->
            embedder.modelId to Vectors.prepare(embedder.embed(key, TextEmbedder.Kind.QUERY), embedder.dimensions)
        }?.also { synchronized(queryCache) { queryCache[key] = it } } ?: return emptyList()
        val (model, queryVector) = result
        val index = index(model, queryVector.size)
        if (index.size == 0) {
            // Possibly a vector from a model that has since been replaced.
            if (cachedQuery != null) synchronized(queryCache) { queryCache.clear() }
            return emptyList()
        }
        return withContext(Dispatchers.Default) { MeaningSelection.select(index.scores(queryVector), minSimilarity, limit, minZ) }
    }

    private suspend fun index(model: String, dims: Int): MeaningIndex = lock.withLock {
        val signature = dao.vectorSignature()
        cached?.let { (m, s, index) -> if (m == model && s == signature) return index }
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
        }
        val index = builder.build()
        cached = Triple(model, signature, index)
        index
    }

    companion object {
        const val LIMIT = 100
        private const val QUERY_CACHE_SIZE = 64
        private const val PAGE = 500
    }
}
