package com.eyal98.stickerfinder.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.eyal98.stickerfinder.StickerFinderApp
import com.eyal98.stickerfinder.data.TagSuggestions
import com.eyal98.stickerfinder.index.EmbedWorker
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
    /** Per tag (lower case), the stickers the user unticked: they don't get the tag. */
    val unticked: Map<String, Set<Long>> = emptyMap(),
    /** A tag being saved: its buttons are off until it's done. */
    val saving: String? = null,
)

/**
 * The tag suggestions screen: the user's own tags suggested on look-alike stickers, one group per
 * tag, to approve at once. Approved tags become the stickers' own tags (more examples, so the next
 * round of suggestions is better); unticked and rejected ones are hidden on those stickers, so
 * they aren't suggested there again.
 */
class SuggestionsViewModel(private val app: StickerFinderApp) : ViewModel() {

    private val repository = app.repository
    private val unticked = MutableStateFlow<Map<String, Set<Long>>>(emptyMap())
    private val saving = MutableStateFlow<String?>(null)

    /** Tags skipped for now: moved to the end of the list. */
    private val skipped = MutableStateFlow<List<String>>(emptyList())

    val state: StateFlow<SuggestionsUiState> = combine(
        repository.withLearnedTags(),
        unticked,
        saving,
        skipped,
    ) { stickers, off, busy, later ->
        val groups = TagSuggestions.group(stickers)
            .sortedBy { g -> later.indexOf(g.tag.lowercase()) }
        SuggestionsUiState(loading = false, groups = groups, unticked = off, saving = busy)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SuggestionsUiState())

    fun toggle(tag: String, stickerId: Long) = unticked.update { all ->
        val key = tag.lowercase()
        val set = all[key].orEmpty()
        all + (key to if (stickerId in set) set - stickerId else set + stickerId)
    }

    /** Adds [group]'s tag to the ticked stickers, and hides it on the unticked ones. */
    fun accept(group: TagSuggestions.Group) = save(group) { ids, off ->
        repository.addTags(ids - off, listOf(group.tag))
        repository.editHiddenImageTags(off, hide = listOf(group.tag), show = emptyList())
    }

    /** None of them: the tag is hidden on all of [group]'s stickers. */
    fun reject(group: TagSuggestions.Group) = save(group) { ids, _ ->
        repository.editHiddenImageTags(ids, hide = listOf(group.tag), show = emptyList())
    }

    fun skip(group: TagSuggestions.Group) = skipped.update { (it - group.tag.lowercase()) + group.tag.lowercase() }

    private fun save(group: TagSuggestions.Group, block: suspend (Set<Long>, Set<Long>) -> Unit) {
        if (saving.value != null) return
        val key = group.tag.lowercase()
        saving.value = key
        viewModelScope.launch {
            try {
                val ids = group.stickers.map { it.id }.toSet()
                block(ids, unticked.value[key].orEmpty() intersect ids)
                unticked.update { it - key }
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
