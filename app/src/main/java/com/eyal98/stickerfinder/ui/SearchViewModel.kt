package com.eyal98.stickerfinder.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.eyal98.stickerfinder.StickerFinderApp
import com.eyal98.stickerfinder.data.StickerEntity
import com.eyal98.stickerfinder.data.StickerRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.sample
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class SearchUiState(
    val results: List<StickerEntity> = emptyList(),
    val total: Int = 0,
    val pending: Int = 0,
    val isQueryBlank: Boolean = true,
)

@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
class SearchViewModel(private val app: StickerFinderApp) : ViewModel() {

    private val repository: StickerRepository = app.repository

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    /** Bumped after edits (star, tags) so the current search runs again. */
    private val refresh = MutableStateFlow(0)

    /**
     * Background work finishing more stickers (printed text, picture tags, meaning vectors):
     * the current search runs again so results found by that work show up, at most every few
     * seconds while it runs.
     */
    private val indexChanges = combine(
        repository.pendingCount,
        repository.vectorCount,
        app.database.stickerDao().observeImageTagPendingCount(),
    ) { pending, vectors, untagged -> Triple(pending, vectors, untagged) }
        .distinctUntilChanged()
        .drop(1)
        .sample(INDEX_CHANGE_SAMPLE_MS)
        .map { }
        .onStart { emit(Unit) }

    private val results = combine(_query.debounce(DEBOUNCE_MS), refresh, indexChanges) { q, _, _ -> q }
        .flatMapLatest { q ->
            if (q.isBlank()) {
                repository.browse()
            } else {
                flow {
                    // Keyword results are instant; the merged ranking follows once the query
                    // has been embedded (the first query also loads the model).
                    val keyword = repository.searchKeywords(q)
                    emit(keyword)
                    // The meaning search runs the embedding model: only once typing pauses.
                    // A new keystroke cancels this flow before it gets there.
                    delay(MEANING_PAUSE_MS)
                    val hybrid = repository.search(q)
                    if (hybrid != keyword) emit(hybrid)
                }
            }
        }

    val uiState: StateFlow<SearchUiState> =
        combine(results, repository.stickerCount, repository.pendingCount, _query) { r, total, pending, q ->
            SearchUiState(results = r, total = total, pending = pending, isQueryBlank = q.isBlank())
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SearchUiState())

    fun onQueryChange(value: String) {
        _query.value = value
    }

    fun toggleStar(sticker: StickerEntity) = edit { repository.setStarred(sticker.id, !sticker.starred) }

    /** Runs the current search again, e.g. after a sticker was edited. */
    fun refresh() {
        refresh.value++
    }

    fun onSent(sticker: StickerEntity) = edit { repository.recordUse(sticker.id) }

    private fun edit(block: suspend () -> Unit) {
        viewModelScope.launch {
            block()
            refresh.value++
        }
    }

    companion object {
        private const val DEBOUNCE_MS = 150L
        private const val MEANING_PAUSE_MS = 350L
        private const val INDEX_CHANGE_SAMPLE_MS = 3_000L

        val Factory = viewModelFactory {
            initializer { SearchViewModel(this[APPLICATION_KEY] as StickerFinderApp) }
        }
    }
}
