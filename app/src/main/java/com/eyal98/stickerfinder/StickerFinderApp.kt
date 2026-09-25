package com.eyal98.stickerfinder

import android.app.Application
import android.content.ComponentCallbacks2
import com.eyal98.stickerfinder.data.GoldenSetStore
import com.eyal98.stickerfinder.data.SearchEvaluator
import com.eyal98.stickerfinder.data.SearchSettings
import com.eyal98.stickerfinder.data.SemanticSearch
import com.eyal98.stickerfinder.data.StickerDatabase
import com.eyal98.stickerfinder.data.StickerRepository
import com.eyal98.stickerfinder.embed.EmbedderHolder
import com.eyal98.stickerfinder.index.StickerIndexHost
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class StickerFinderApp : Application(), StickerIndexHost {
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override val database: StickerDatabase by lazy { StickerDatabase.create(this) }
    override val embedders: EmbedderHolder by lazy { EmbedderHolder(this) }
    val searchSettings: SearchSettings by lazy { SearchSettings(this) }
    private val semanticSearch: SemanticSearch by lazy {
        SemanticSearch(database.stickerDao(), embedders) { searchSettings.minSimilarity }
    }
    override val repository: StickerRepository by lazy { StickerRepository(database.stickerDao(), semanticSearch) }
    val goldenSet: GoldenSetStore by lazy { GoldenSetStore(this) }
    val evaluator: SearchEvaluator by lazy {
        SearchEvaluator(database.stickerDao(), repository, semanticSearch, searchSettings)
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        // The embedding model takes a few hundred MB; free it once the UI is hidden. It reloads
        // on the next search (or indexing run) in about a second.
        if (level >= ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN) {
            appScope.launch { embedders.release() }
        }
    }
}
