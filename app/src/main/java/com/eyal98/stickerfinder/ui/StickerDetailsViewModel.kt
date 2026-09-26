package com.eyal98.stickerfinder.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.eyal98.stickerfinder.StickerFinderApp
import com.eyal98.stickerfinder.data.ImageTagFilter
import com.eyal98.stickerfinder.data.LookAlike
import com.eyal98.stickerfinder.data.StickerEntity
import com.eyal98.stickerfinder.data.StickerFaceInfo
import com.eyal98.stickerfinder.data.UserTags
import com.eyal98.stickerfinder.index.EmbedWorker
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Stickers to share an edit with, of one kind; candidates load when the picker first opens. */
data class ShareSet(
    val candidates: List<LookAlike>? = null,
    val selected: Set<Long> = emptySet(),
)

data class StickerDetailsState(
    val loaded: Boolean = false,
    val tags: List<String> = emptyList(),
    /** What's typed in the "add a tag" field. */
    val newTag: String = "",
    val description: String = "",
    /** Picture tags the user hid, including ones hidden in this edit. */
    val removedPictureTags: Set<String> = emptySet(),
    val looks: ShareSet = ShareSet(),
    val context: ShareSet = ShareSet(),
    val samePerson: List<Long> = emptyList(),
    val shareSamePerson: Boolean = false,
    val packSize: Int = 0,
    val sharePack: Boolean = false,
    val saving: Boolean = false,
) {
    val shareCount: Int get() = (looks.selected + context.selected).size +
        (if (shareSamePerson) samePerson.size else 0) + (if (sharePack) packSize - 1 else 0)
}

/**
 * The sticker details screen: edit the sticker's own tags and description, hide wrong picture
 * tags, fix the people on it, and share the edit with stickers that look alike, mean the same,
 * show the same person, or come from the same pack.
 */
class StickerDetailsViewModel(private val app: StickerFinderApp, private val id: Long) : ViewModel() {

    private val repository = app.repository
    private val dao = app.database.stickerDao()

    val sticker: StateFlow<StickerEntity?> =
        dao.observeSticker(id).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    val faces: StateFlow<List<StickerFaceInfo>> =
        dao.observeFacesOnSticker(id).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _state = MutableStateFlow(StickerDetailsState())
    val state: StateFlow<StickerDetailsState> = _state.asStateFlow()

    /** What the sticker had when the screen opened, to know what this edit changed. */
    private var original: StickerEntity? = null

    init {
        viewModelScope.launch {
            val s = dao.byId(id) ?: return@launch
            original = s
            _state.value = StickerDetailsState(
                loaded = true,
                tags = UserTags.parse(s.userTags),
                description = s.userDescription.orEmpty(),
                removedPictureTags = ImageTagFilter.split(s.removedImageTags).toSet(),
                samePerson = repository.samePersonStickers(id),
                packSize = s.packName?.let { repository.packSize(it) } ?: 0,
            )
        }
    }

    fun setNewTag(value: String) = _state.update { it.copy(newTag = value) }

    /** Adds what's typed as a tag (commas separate several). */
    fun addTag() = _state.update { s ->
        val added = s.newTag.split(',').map { it.trim() }.filter { it.isNotEmpty() }
        s.copy(tags = UserTags.parse(UserTags.format(s.tags + added)), newTag = "")
    }

    fun removeTag(tag: String) = _state.update { it.copy(tags = it.tags - tag) }
    fun setDescription(value: String) = _state.update { it.copy(description = value) }
    fun hidePictureTag(tag: String) = _state.update { it.copy(removedPictureTags = it.removedPictureTags + tag) }
    fun showPictureTag(tag: String) = _state.update { it.copy(removedPictureTags = it.removedPictureTags - tag) }
    fun setShareSamePerson(on: Boolean) = _state.update { it.copy(shareSamePerson = on) }
    fun setSharePack(on: Boolean) = _state.update { it.copy(sharePack = on) }

    /** Loads look-alike candidates, the closest ones picked. */
    fun loadLooks() {
        if (_state.value.looks.candidates != null) return
        viewModelScope.launch {
            val found = repository.lookAlikes(id)
            _state.update { it.copy(looks = ShareSet(found, found.filter { c -> c.similarity >= LOOK_PRESELECT }.map { c -> c.sticker.id }.toSet())) }
        }
    }

    /** Loads same-context candidates; none picked, since meaning is looser than looks. */
    fun loadContext() {
        if (_state.value.context.candidates != null) return
        viewModelScope.launch {
            _state.update { it.copy(context = ShareSet(repository.contextAlikes(id))) }
        }
    }

    fun toggleLook(stickerId: Long) = _state.update { s ->
        s.copy(looks = s.looks.copy(selected = s.looks.selected.toggle(stickerId)))
    }

    fun toggleContext(stickerId: Long) = _state.update { s ->
        s.copy(context = s.context.copy(selected = s.context.selected.toggle(stickerId)))
    }

    private fun Set<Long>.toggle(x: Long) = if (x in this) this - x else this + x

    /** Renames the group of a face on this sticker (every sticker of that person). */
    fun renamePerson(personId: Long, name: String) = viewModelScope.launch {
        dao.renamePerson(personId, name.trim().ifEmpty { null })
        if (dao.syncPeopleNames() > 0) EmbedWorker.runForEdit(app)
    }

    /** "Not this person": takes a face on this sticker out of its group. */
    fun removeFace(faceId: Long) = viewModelScope.launch {
        dao.removeFaceFromGroup(faceId)
        dao.deleteEmptyPeople()
        if (dao.syncPeopleNames() > 0) EmbedWorker.runForEdit(app)
    }

    /**
     * Saves this sticker's fields, and applies to the chosen stickers only what this edit
     * changed: tags added and removed, picture tags hidden and brought back, and the description
     * if it changed. Everything else on those stickers stays as it was.
     */
    fun save(onDone: () -> Unit) {
        // A tag still in the field counts, as if Add was tapped.
        if (_state.value.newTag.isNotBlank()) addTag()
        val s = _state.value
        val before = original ?: return
        _state.update { it.copy(saving = true) }
        viewModelScope.launch {
            val description = s.description.trim().ifEmpty { null }
            repository.setTags(id, s.tags)
            repository.setDescription(listOf(id), description)
            repository.setRemovedImageTags(id, s.removedPictureTags)

            val targets = buildSet {
                addAll(s.looks.selected)
                addAll(s.context.selected)
                if (s.shareSamePerson) addAll(s.samePerson)
                if (s.sharePack) before.packName?.let { addAll(repository.packStickers(it)) }
                remove(id)
            }
            if (targets.isNotEmpty()) {
                val oldTags = UserTags.parse(before.userTags)
                val added = s.tags.filter { t -> oldTags.none { it.equals(t, ignoreCase = true) } }
                val removed = oldTags.filter { t -> s.tags.none { it.equals(t, ignoreCase = true) } }
                repository.editTags(targets, add = added, remove = removed)

                val oldHidden = ImageTagFilter.split(before.removedImageTags)
                val hidden = s.removedPictureTags.filter { t -> oldHidden.none { it.equals(t, ignoreCase = true) } }
                val shown = oldHidden.filter { t -> s.removedPictureTags.none { it.equals(t, ignoreCase = true) } }
                repository.editHiddenImageTags(targets, hide = hidden, show = shown)

                val oldDescription = before.userDescription
                when {
                    description == oldDescription -> Unit
                    description != null -> repository.setDescription(targets, description)
                    // Cleared: clear it where it's the same text, keep other stickers' own.
                    oldDescription != null -> repository.clearDescription(targets, oldDescription)
                }
            }
            EmbedWorker.runForEdit(app)
            onDone()
        }
    }

    companion object {
        /** Look-alikes at least this close start out picked (as in the old look-alike offer). */
        private const val LOOK_PRESELECT = 0.85f

        fun factory(id: Long) = viewModelFactory {
            initializer { StickerDetailsViewModel(this[androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as StickerFinderApp, id) }
        }
    }
}
