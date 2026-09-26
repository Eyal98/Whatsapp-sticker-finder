package com.eyal98.stickerfinder.data

import com.eyal98.stickerfinder.search.FtsQueryBuilder
import com.eyal98.stickerfinder.search.QueryParser
import com.eyal98.stickerfinder.search.RankFusion
import com.eyal98.stickerfinder.search.Vectors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.util.PriorityQueue
import kotlin.math.ln

class StickerRepository(
    private val dao: StickerDao,
    private val semantic: SemanticSearch? = null,
) {

    val stickerCount: Flow<Int> = dao.observeCount()
    val pendingCount: Flow<Int> = dao.observePendingCount(IndexVersion.CURRENT)
    val captionPendingCount: Flow<Int> = dao.observeCaptionPendingCount()
    val vectorCount: Flow<Int> = dao.observeVectorCount()

    fun browse(limit: Int = BROWSE_LIMIT): Flow<List<StickerEntity>> = dao.observeBrowse(limit)

    /**
     * Keyword search in Hebrew and English. Every query word must match; if nothing does, falls
     * back to stickers matching any word. Fast, so it's shown while semantic search runs.
     */
    suspend fun searchKeywords(query: String, limit: Int = SEARCH_LIMIT): List<StickerEntity> {
        val terms = QueryParser.parse(query)
        val all = FtsQueryBuilder.matchAll(terms) ?: return emptyList()
        val results = dao.searchFts(all, limit).ifEmpty {
            FtsQueryBuilder.matchAny(terms)?.let { dao.searchFts(it, limit) }.orEmpty()
        }
        return dedupe(results)
    }

    /**
     * Keyword and meaning-based results merged with Reciprocal Rank Fusion, with a small boost
     * for starred and often-used stickers. Same as [searchKeywords] when no embedding model is
     * installed.
     */
    suspend fun search(query: String, limit: Int = SEARCH_LIMIT): List<StickerEntity> {
        val keyword = searchKeywords(query, limit)
        val semanticIds = semantic?.search(query).orEmpty()
        if (semanticIds.isEmpty()) return keyword

        val byId = keyword.associateBy { it.id }.toMutableMap()
        val missing = semanticIds.filterNot(byId::containsKey)
        if (missing.isNotEmpty()) dao.byIds(missing).forEach { byId[it.id] = it }
        return dedupe(fuse(keyword.map { it.id }, semanticIds, byId).mapNotNull(byId::get)).take(limit)
    }

    suspend fun setStarred(id: Long, starred: Boolean) = dao.setStarred(id, starred)

    suspend fun recordUse(id: Long) = dao.recordUse(id, System.currentTimeMillis())

    suspend fun setTags(id: Long, tags: String) {
        dao.setTags(id, tags.trim())
        refreshSearchTerms(id)
    }

    /** Adds [tags] to each sticker's own tags, keeping the ones it has. */
    suspend fun addTags(ids: Collection<Long>, tags: List<String>) {
        for (sticker in dao.byIds(ids.toList())) {
            val merged = (sticker.userTags.split(' ') + tags).map { it.trim() }.filter { it.isNotEmpty() }.distinct()
            dao.setTags(sticker.id, merged.joinToString(" "))
            refreshSearchTerms(sticker.id)
        }
    }

    /**
     * The stickers whose pictures look most like sticker [id]'s, closest first, by the image
     * model's vectors (empty until that sticker is picture-tagged). Copies of one image appear
     * once. Used to spread a tag the models can't know, like a local TV show, to its look-alikes.
     */
    suspend fun lookAlikes(id: Long, limit: Int = LOOK_ALIKE_LIMIT): List<LookAlike> = withContext(Dispatchers.Default) {
        val target = dao.imageVector(id) ?: return@withContext emptyList()
        val query = Vectors.decode(target.vector)
        val best = PriorityQueue<Pair<Long, Float>>(compareBy { it.second })
        var after = -1L
        while (true) {
            val page = dao.imageVectorPage(target.model, after, VECTOR_PAGE)
            if (page.isEmpty()) break
            for (row in page) {
                if (row.stickerId == id) continue
                val v = Vectors.decode(row.vector)
                if (v.size != query.size) continue
                best.add(row.stickerId to Vectors.dot(query, v))
                // Room for copies of the same image, which are dropped below.
                if (best.size > limit * 2) best.poll()
            }
            after = page.last().stickerId
        }
        val scores = best.associate { it }
        val self = dao.byId(id)?.let(::imageKey)
        dao.byIds(scores.keys.toList())
            .sortedByDescending { scores.getValue(it.id) }
            .distinctBy(::imageKey)
            .filter { imageKey(it) != self }
            .take(limit)
            .map { LookAlike(it, scores.getValue(it.id)) }
    }

    /** Rebuilds the full-text entry of one sticker from its current text fields. */
    suspend fun refreshSearchTerms(id: Long) = dao.refreshFts(id)

    companion object {
        const val BROWSE_LIMIT = 500
        const val SEARCH_LIMIT = 100
        const val LOOK_ALIKE_LIMIT = 60
        private const val VECTOR_PAGE = 500
        private const val STAR_BOOST = 0.004
        private const val USE_BOOST = 0.001

        /** Merges keyword and semantic rankings; [stickers] must hold every id in both. */
        fun fuse(keywordIds: List<Long>, semanticIds: List<Long>, stickers: Map<Long, StickerEntity>): List<Long> {
            if (semanticIds.isEmpty()) return keywordIds
            val boosts = (keywordIds + semanticIds).mapNotNull(stickers::get).associate { it.id to boost(it) }
            return RankFusion.fuse(listOf(keywordIds, semanticIds), boosts)
        }

        /** Stable identity of a sticker's image; copies of the same image share it. */
        fun imageKey(s: StickerEntity): String =
            s.perceptualHash?.let { java.lang.Long.toHexString(it) } ?: "id:${s.id}"

        /** Copies of the same image are shown once. */
        fun dedupe(results: List<StickerEntity>) = results.distinctBy(::imageKey)

        /** Small next to RRF scores (about 1/60 for a top hit), so it only reorders close calls. */
        private fun boost(s: StickerEntity): Double =
            (if (s.starred) STAR_BOOST else 0.0) + USE_BOOST * ln(1.0 + s.useCount)
    }
}

/** A sticker whose picture looks like another's; [similarity] is the cosine of their vectors. */
data class LookAlike(val sticker: StickerEntity, val similarity: Float)
