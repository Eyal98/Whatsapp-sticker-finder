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

@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
class SearchViewModel(private val repository: StickerRepository) : ViewModel() {

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    /** Bumped after edits (star, tags) so the current search runs again. */
    private val refresh = MutableStateFlow(0)

    private val results = combine(_query.debounce(DEBOUNCE_MS), refresh) { q, _ -> q }
        .flatMapLatest { q ->
            if (q.isBlank()) repository.browse() else flow { emit(repository.search(q)) }
        }

    val uiState: StateFlow<SearchUiState> =
        combine(results, repository.stickerCount, repository.pendingCount, _query) { r, total, pending, q ->
            SearchUiState(results = r, total = total, pending = pending, isQueryBlank = q.isBlank())
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SearchUiState())

    fun onQueryChange(value: String) {
        _query.value = value
    }

    fun toggleStar(sticker: StickerEntity) = edit { repository.setStarred(sticker.id, !sticker.starred) }

    fun setTags(sticker: StickerEntity, tags: String) = edit { repository.setTags(sticker.id, tags) }

    fun onSent(sticker: StickerEntity) = edit { repository.recordUse(sticker.id) }

    private fun edit(block: suspend () -> Unit) {
        viewModelScope.launch {
            block()
            refresh.value++
        }
    }

    companion object {
        private const val DEBOUNCE_MS = 150L

        val Factory = viewModelFactory {
            initializer { SearchViewModel((this[APPLICATION_KEY] as StickerFinderApp).repository) }
        }
    }
}
