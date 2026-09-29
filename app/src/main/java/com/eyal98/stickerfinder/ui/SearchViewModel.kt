package com.eyal98.stickerfinder.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.eyal98.stickerfinder.StickerFinderApp
import com.eyal98.stickerfinder.data.FolderSummary
import com.eyal98.stickerfinder.data.StickerEntity
import com.eyal98.stickerfinder.data.StickerRepository
import com.eyal98.stickerfinder.data.TagSuggestions
import com.eyal98.stickerfinder.index.EmbedWorker
import com.eyal98.stickerfinder.index.ImageTagStatus
import com.eyal98.stickerfinder.search.StableOrder
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
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.sample
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class SearchUiState(
    val results: List<StickerEntity> = emptyList(),
    val total: Int = 0,
    val pending: Int = 0,
    val isQueryBlank: Boolean = true,
    val imageTags: ImageTagStatus = ImageTagStatus(ImageTagStatus.Phase.DONE, 0, 0),
    /** Tags suggested on look-alike stickers, waiting for review. */
    val suggestedTags: Int = 0,
    val folders: List<FolderSummary> = emptyList(),
    /** The folder being browsed (and searched in), or null for all stickers. */
    val selectedFolder: Long? = null,
)

@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
class SearchViewModel(private val app: StickerFinderApp) : ViewModel() {

    private val repository: StickerRepository = app.repository

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    /** Bumped after edits (star, tags) so the current search runs again. */
    private val refresh = MutableStateFlow(0)

    /** Why the results are being worked out again; each changes them in its own way. */
    private enum class Why {
        /** The user typed: keyword results at once, then the full ranking. */
        QUERY,

        /** The user starred or tagged a sticker: the full ranking, in one step. */
        EDIT,

        /** Background work finished more stickers: what's shown stays put, new finds go last. */
        BACKGROUND,
    }

    private class Run(val query: String, val why: Why, val folder: Long?)

    /**
     * Background work finishing more stickers (printed text, picture tags, meaning vectors). While
     * it runs (hours, on a new phone or after an update), this fires every few seconds.
     */
    private val indexChanges = combine(
        repository.pendingCount,
        repository.vectorCount,
        app.database.stickerDao().observeImageTagPendingCount(),
    ) { pending, vectors, untagged -> Triple(pending, vectors, untagged) }
        .distinctUntilChanged()
        .drop(1)
        .sample(INDEX_CHANGE_SAMPLE_MS)

    /** The search the results on screen are for. */
    private val searched = _query.debounce(DEBOUNCE_MS).distinctUntilChanged()

    /** What's on screen, and the search it's for, so updates keep to it and keep it in place. */
    private var shown: List<StickerEntity> = emptyList()
    private var shownQuery = ""

    private val selectedFolder = MutableStateFlow<Long?>(null)

    private val runs = merge(
        searched.map { Run(it, Why.QUERY, selectedFolder.value) },
        // Opening or leaving a folder: the same search, in the new place.
        selectedFolder.drop(1).map { Run(shownQuery, Why.QUERY, it) },
        // The search on screen, not text still being typed.
        refresh.drop(1).map { Run(shownQuery, Why.EDIT, selectedFolder.value) },
        indexChanges.map { Run(shownQuery, Why.BACKGROUND, selectedFolder.value) },
    )
        // With no search, the browse list (or the folder) follows the database by itself.
        .filter { it.why == Why.QUERY || it.query.isNotBlank() }

    private val results = runs
        .flatMapLatest { run ->
            val q = run.query
            val folder = run.folder
            shownQuery = q
            // Searching in a folder: search everything, keep what's in the folder. Looks further
            // down the ranking, since most results won't be in it.
            val limit = if (folder == null) StickerRepository.SEARCH_LIMIT else FOLDER_SEARCH_LIMIT
            suspend fun search(keywordsOnly: Boolean): List<StickerEntity> {
                val all = if (keywordsOnly) repository.searchKeywords(q, limit) else repository.search(q, limit)
                if (folder == null) return all
                val ids = repository.folderStickerIds(folder)
                return all.filter { it.id in ids }
            }
            when {
                q.isBlank() && folder != null -> repository.folderStickers(folder)
                q.isBlank() -> repository.browse()
                run.why == Why.QUERY -> flow {
                    // Keyword results are instant; the merged ranking follows once the query
                    // has been embedded (the first query also loads the model).
                    emit(search(keywordsOnly = true))
                    // The meaning search runs the embedding model: only once typing pauses.
                    // A new keystroke cancels this flow before it gets there.
                    delay(MEANING_PAUSE_MS)
                    emit(search(keywordsOnly = false))
                }
                run.why == Why.EDIT -> flow { emit(search(keywordsOnly = false)) }
                // Never reshuffle what the user is looking at: stickers on screen keep their place.
                else -> flow { emit(StableOrder.merge(shown, search(keywordsOnly = false)) { it.id }) }
            }
        }
        .distinctUntilChanged()
        .onEach { shown = it }

    val uiState: StateFlow<SearchUiState> =
        combine(
            results,
            repository.stickerCount,
            repository.pendingCount,
            _query,
            combine(
                ImageTagStatus.observe(app, app.database.stickerDao()),
                repository.withLearnedTags().map { TagSuggestions.group(it).size }.distinctUntilChanged(),
                repository.folders,
                selectedFolder,
            ) { tags, suggested, folders, folder -> Extras(tags, suggested, folders, folder) },
        ) { r, total, pending, q, extras ->
            SearchUiState(
                results = r,
                total = total,
                pending = pending,
                isQueryBlank = q.isBlank(),
                imageTags = extras.tags,
                suggestedTags = extras.suggested,
                folders = extras.folders,
                // A deleted folder can't stay selected.
                selectedFolder = extras.folder?.takeIf { id -> extras.folders.any { it.id == id } },
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SearchUiState())

    private class Extras(val tags: ImageTagStatus, val suggested: Int, val folders: List<FolderSummary>, val folder: Long?)

    fun selectFolder(id: Long?) {
        selectedFolder.value = id
    }

    /** Makes a folder and opens it, ready to fill from stickers' details. */
    fun createFolder(name: String) = viewModelScope.launch { selectedFolder.value = repository.createFolder(name) }

    fun renameFolder(id: Long, name: String) = viewModelScope.launch { repository.renameFolder(id, name) }

    fun deleteFolder(id: Long) = viewModelScope.launch {
        repository.deleteFolder(id)
        if (selectedFolder.value == id) selectedFolder.value = null
    }

    fun onQueryChange(value: String) {
        _query.value = value
        if (value.isNotBlank()) Onboarding.complete(app, Onboarding.Step.SEARCH)
    }

    fun toggleStar(sticker: StickerEntity) = edit {
        repository.setStarred(sticker.id, !sticker.starred)
        if (!sticker.starred) Onboarding.complete(app, Onboarding.Step.STAR)
    }

    /** Runs the current search again, e.g. after a sticker was edited. */
    fun refresh() {
        refresh.value++
    }

    /** Counts the use, and remembers the pick for this search so search learns from it. */
    fun onSent(sticker: StickerEntity) = edit {
        Onboarding.complete(app, Onboarding.Step.SEND)
        repository.recordUse(sticker.id)
        // Sent twice for one search: the search may have become a tag on it.
        if (repository.recordPick(_query.value, sticker.id)) EmbedWorker.runForEdit(app)
    }

    private fun edit(block: suspend () -> Unit) {
        viewModelScope.launch {
            block()
            refresh.value++
        }
    }

    companion object {
        private const val DEBOUNCE_MS = 150L
        private const val MEANING_PAUSE_MS = 350L
        private const val FOLDER_SEARCH_LIMIT = 500
        /** Background updates at most this often: each one runs the full search again. */
        private const val INDEX_CHANGE_SAMPLE_MS = 10_000L

        val Factory = viewModelFactory {
            initializer { SearchViewModel(this[APPLICATION_KEY] as StickerFinderApp) }
        }
    }
}
