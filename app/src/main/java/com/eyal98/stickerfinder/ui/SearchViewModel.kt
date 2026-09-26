package com.eyal98.stickerfinder.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.eyal98.stickerfinder.StickerFinderApp
import com.eyal98.stickerfinder.data.LookAlike
import com.eyal98.stickerfinder.data.StickerEntity
import com.eyal98.stickerfinder.data.StickerRepository
import com.eyal98.stickerfinder.index.EmbedWorker
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class SearchUiState(
    val results: List<StickerEntity> = emptyList(),
    val total: Int = 0,
    val pending: Int = 0,
    val isQueryBlank: Boolean = true,
)

/** Offered after the user adds tags to a sticker: look-alike stickers that could get them too. */
data class LookAlikeOffer(
    val tags: List<String>,
    val candidates: List<LookAlike>,
    /** Starts with the closest matches picked; the user changes it. */
    val selected: Set<Long>,
)

@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
class SearchViewModel(private val app: StickerFinderApp) : ViewModel() {

    private val repository: StickerRepository = app.repository

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    /** Bumped after edits (star, tags) so the current search runs again. */
    private val refresh = MutableStateFlow(0)

    private val results = combine(_query.debounce(DEBOUNCE_MS), refresh) { q, _ -> q }
        .flatMapLatest { q ->
            if (q.isBlank()) {
                repository.browse()
            } else {
                flow {
                    // Keyword results are instant; the merged ranking follows once the query
                    // has been embedded (the first query also loads the model).
                    val keyword = repository.searchKeywords(q)
                    emit(keyword)
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

    private val _lookAlikes = MutableStateFlow<LookAlikeOffer?>(null)
    val lookAlikes: StateFlow<LookAlikeOffer?> = _lookAlikes.asStateFlow()

    fun setTags(sticker: StickerEntity, tags: String) = edit {
        repository.setTags(sticker.id, tags)
        EmbedWorker.runNow(app)
        // New words only: re-saving existing tags shouldn't ask again.
        val old = sticker.userTags.split(' ').filter { it.isNotBlank() }.toSet()
        val added = tags.split(' ').map { it.trim() }.filter { it.isNotEmpty() && it !in old }.distinct()
        if (added.isEmpty()) return@edit
        val candidates = repository.lookAlikes(sticker.id)
        if (candidates.isEmpty()) return@edit
        _lookAlikes.value = LookAlikeOffer(
            tags = added,
            candidates = candidates,
            selected = candidates.filter { it.similarity >= PRESELECT_SIMILARITY }.map { it.sticker.id }.toSet(),
        )
    }

    fun toggleLookAlike(id: Long) {
        _lookAlikes.value = _lookAlikes.value?.let { offer ->
            offer.copy(selected = if (id in offer.selected) offer.selected - id else offer.selected + id)
        }
    }

    fun applyLookAlikes() {
        val offer = _lookAlikes.value ?: return
        _lookAlikes.value = null
        if (offer.selected.isEmpty()) return
        edit {
            repository.addTags(offer.selected, offer.tags)
            EmbedWorker.runNow(app)
        }
    }

    fun dismissLookAlikes() {
        _lookAlikes.value = null
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

        /**
         * Look-alikes at least this close start out picked. A cautious first guess (not tuned
         * on real stickers yet): a wrong pick costs a tap, a wrong tag costs a bad search result.
         */
        private const val PRESELECT_SIMILARITY = 0.85f

        val Factory = viewModelFactory {
            initializer { SearchViewModel(this[APPLICATION_KEY] as StickerFinderApp) }
        }
    }
}
