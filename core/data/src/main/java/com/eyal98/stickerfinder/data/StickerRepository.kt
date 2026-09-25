package com.eyal98.stickerfinder.data

import com.eyal98.stickerfinder.search.FtsQueryBuilder
import com.eyal98.stickerfinder.search.IndexTerms
import com.eyal98.stickerfinder.search.QueryParser
import kotlinx.coroutines.flow.Flow

class StickerRepository(private val dao: StickerDao) {

    val stickerCount: Flow<Int> = dao.observeCount()
    val pendingCount: Flow<Int> = dao.observePendingCount()

    fun browse(limit: Int = BROWSE_LIMIT): Flow<List<StickerEntity>> = dao.observeBrowse(limit)

    /**
     * Keyword search in Hebrew and English. Every query word must match; if nothing does, falls
     * back to stickers matching any word. Copies of the same image are shown once.
     */
    suspend fun search(query: String, limit: Int = SEARCH_LIMIT): List<StickerEntity> {
        val terms = QueryParser.parse(query)
        val all = FtsQueryBuilder.matchAll(terms) ?: return emptyList()
        val results = dao.searchFts(all, limit).ifEmpty {
            FtsQueryBuilder.matchAny(terms)?.let { dao.searchFts(it, limit) }.orEmpty()
        }
        return results.distinctBy { it.perceptualHash?.let { h -> "hash:$h" } ?: "id:${it.id}" }
    }

    suspend fun setStarred(id: Long, starred: Boolean) = dao.setStarred(id, starred)

    suspend fun recordUse(id: Long) = dao.recordUse(id, System.currentTimeMillis())

    suspend fun setTags(id: Long, tags: String) {
        dao.setTags(id, tags.trim())
        refreshSearchTerms(id)
    }

    /** Rebuilds the full-text entry of one sticker from its current text fields. */
    suspend fun refreshSearchTerms(id: Long) {
        val s = dao.byId(id) ?: return
        dao.replaceFts(StickerFts(s.id, IndexTerms.build(s.ocrText, s.captionHe, s.captionEn, s.userTags)))
    }

    companion object {
        const val BROWSE_LIMIT = 500
        const val SEARCH_LIMIT = 100
    }
}
