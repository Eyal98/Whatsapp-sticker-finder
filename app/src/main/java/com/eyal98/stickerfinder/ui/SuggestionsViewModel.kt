package com.eyal98.stickerfinder.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.eyal98.stickerfinder.StickerFinderApp
import com.eyal98.stickerfinder.data.ImageTagFilter
import com.eyal98.stickerfinder.data.StickerEntity
import com.eyal98.stickerfinder.data.TagSuggestions
import com.eyal98.stickerfinder.index.EmbedWorker
import com.eyal98.stickerfinder.search.StableOrder
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SuggestionsUiState(
    val loading: Boolean = true,
    val groups: List<TagSuggestions.Group> = emptyList(),
    /** A tag being saved: its buttons are off until it's done. */
    val saving: String? = null,
    /** The last sticker taken out of a tag's suggestions, which Undo puts back. */
    val lastDiscard: Discard? = null,
)

/** A sticker (with its copies, [ids]) taken out of [tag]'s suggestions for good. */
data class Discard(val tag: String, val ids: List<Long>)

/**
 * The tag suggestions screen: the user's own tags suggested on look-alike stickers, one group per
 * tag, to approve at once. Approved tags become the stickers' own tags (more examples, so the next
 * round of suggestions is better). A sticker tapped away is hidden for that tag right away, on
 * every copy of its picture, so it isn't suggested for that tag again; Undo brings it back.
 */
class SuggestionsViewModel(private val app: StickerFinderApp) : ViewModel() {

    private val repository = app.repository
    private val saving = MutableStateFlow<String?>(null)
    private val lastDiscard = MutableStateFlow<Discard?>(null)

    /** Tags skipped for now: moved to the end of the list. */
    private val skipped = MutableStateFlow<List<String>>(emptyList())

    val state: StateFlow<SuggestionsUiState> = combine(
        repository.withLearnedTags(),
        saving,
        skipped,
        lastDiscard,
    ) { stickers, busy, later, discard ->
        SuggestionsUiState(loading = false, groups = steady(TagSuggestions.group(stickers), later), saving = busy, lastDiscard = discard)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SuggestionsUiState())

    /** The order groups were first shown in, and each group's stickers in the order shown. */
    private val groupOrder = mutableListOf<String>()
    private val shownStickers = HashMap<String, List<StickerEntity>>()

    /**
     * Keeps the screen still while the user works through it: groups stay where they were first
     * shown (removing a sticker shrinks a group, which used to move it), new groups go last, and
     * a group's stickers keep their places. Only "Later" moves a group, to the end.
     */
    private fun steady(groups: List<TagSuggestions.Group>, later: List<String>): List<TagSuggestions.Group> {
        for (g in groups) {
            val key = g.tag.lowercase()
            if (key !in groupOrder) groupOrder += key
        }
        return groups
            .map { g ->
                val key = g.tag.lowercase()
                val stickers = StableOrder.merge(shownStickers[key].orEmpty(), g.stickers) { it.id }
                shownStickers[key] = stickers
                g.copy(stickers = stickers)
            }
            .sortedWith(compareBy({ later.indexOf(it.tag.lowercase()) }, { groupOrder.indexOf(it.tag.lowercase()) }))
    }

    /** Not this one: [sticker] (and its copies) won't be suggested for [tag] again. */
    fun discard(tag: String, sticker: StickerEntity) = viewModelScope.launch {
        // Copies that already had this tag hidden stay hidden after an undo.
        val ids = repository.withCopies(listOf(sticker.id))
        val already = app.database.stickerDao().byIds(ids)
            .filter { s -> ImageTagFilter.split(s.removedImageTags).any { it.equals(tag, ignoreCase = true) } }
            .map { it.id }.toSet()
        repository.editHiddenImageTags(ids, hide = listOf(tag), show = emptyList())
        lastDiscard.value = Discard(tag, ids - already)
        // Learned tags are recomputed without it (hidden tags are never suggested again).
        EmbedWorker.runForEdit(app)
    }

    fun undoDiscard() = viewModelScope.launch {
        val discard = lastDiscard.value ?: return@launch
        lastDiscard.value = null
        repository.editHiddenImageTags(discard.ids, hide = emptyList(), show = listOf(discard.tag))
        EmbedWorker.runForEdit(app)
    }

    /** Adds [group]'s tag to its stickers (and their copies). */
    fun accept(group: TagSuggestions.Group) = save(group) { ids ->
        repository.addTags(ids, listOf(group.tag))
    }

    /** None of them: the tag is hidden on all of [group]'s stickers (and their copies). */
    fun reject(group: TagSuggestions.Group) = save(group) { ids ->
        repository.editHiddenImageTags(ids, hide = listOf(group.tag), show = emptyList())
    }

    fun skip(group: TagSuggestions.Group) = skipped.update { (it - group.tag.lowercase()) + group.tag.lowercase() }

    private fun save(group: TagSuggestions.Group, block: suspend (List<Long>) -> Unit) {
        if (saving.value != null) return
        val key = group.tag.lowercase()
        saving.value = key
        lastDiscard.value = null
        viewModelScope.launch {
            try {
                block(repository.withCopies(group.stickers.map { it.id }))
                // New examples: learned tags (and meaning vectors) are brought up to date.
                EmbedWorker.runForEdit(app)
            } finally {
                saving.value = null
            }
        }
    }

    companion object {
        val Factory = viewModelFactory {
            initializer { SuggestionsViewModel(this[APPLICATION_KEY] as StickerFinderApp) }
        }
    }
}
