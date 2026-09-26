package com.eyal98.stickerfinder.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.eyal98.stickerfinder.StickerFinderApp
import com.eyal98.stickerfinder.data.FaceOnSticker
import com.eyal98.stickerfinder.data.PersonSummary
import com.eyal98.stickerfinder.index.EmbedWorker
import com.eyal98.stickerfinder.index.FaceSettings
import com.eyal98.stickerfinder.index.FaceWorker
import com.eyal98.stickerfinder.vision.StickerFaces
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class PeopleUiState(
    val available: Boolean = true,
    val enabled: Boolean = false,
    val pending: Int = 0,
    val total: Int = 0,
    val running: Boolean = false,
    val people: List<PersonSummary> = emptyList(),
)

/** The People screen: turning the feature on and off, and naming, fixing and merging groups. */
@OptIn(ExperimentalCoroutinesApi::class)
class PeopleViewModel(private val app: StickerFinderApp) : ViewModel() {

    private val dao = app.database.stickerDao()
    private val enabled = MutableStateFlow(FaceSettings.isEnabled(app))
    private val available = StickerFaces.isBundled(app)

    private val people = dao.observePeople(MIN_STICKERS)

    init {
        // Picks up faces not grouped yet, and rebuilds groups after a grouping change.
        if (FaceSettings.isEnabled(app)) startNow()
    }

    val state: StateFlow<PeopleUiState> = combine(
        enabled,
        dao.observeFaceScanPendingCount(),
        app.repository.stickerCount,
        FaceWorker.observeRunning(app),
        people,
    ) { on, pending, total, running, list ->
        PeopleUiState(available, on, pending, total, running, list)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PeopleUiState(available = available))

    private val selected = MutableStateFlow<Long?>(null)
    val selectedId: StateFlow<Long?> = selected

    /** The selected group's faces. */
    val faces: StateFlow<List<FaceOnSticker>> = selected
        .flatMapLatest { id -> if (id == null) flowOf(emptyList()) else dao.observeFacesOf(id, MAX_FACES) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun cover(personId: Long) = dao.observeFacesOf(personId, 1)

    fun select(id: Long?) {
        selected.value = id
    }

    fun turnOn() {
        FaceSettings.setEnabled(app, true)
        enabled.value = true
        startNow()
    }

    fun startNow() {
        viewModelScope.launch { FaceWorker.startNow(app) }
    }

    /** Turns People off and deletes every face vector, group and name. */
    fun turnOffAndDelete() {
        FaceSettings.setEnabled(app, false)
        enabled.value = false
        selected.value = null
        FaceWorker.cancel(app)
        viewModelScope.launch {
            dao.deleteFaceData()
            EmbedWorker.runForEdit(app)
        }
    }

    fun rename(personId: Long, name: String) = change {
        dao.renamePerson(personId, name.trim().ifEmpty { null })
    }

    fun removeFace(faceId: Long) = change {
        dao.removeFaceFromGroup(faceId)
        dao.deleteEmptyPeople()
    }

    fun merge(from: Long, into: Long) = change {
        dao.mergePeople(from, into)
        selected.value = into
    }

    /** Applies a change, then updates the names stickers are searchable by. */
    private fun change(block: suspend () -> Unit) {
        viewModelScope.launch {
            block()
            if (dao.syncPeopleNames() > 0) EmbedWorker.runForEdit(app)
        }
    }

    companion object {
        /** Groups on a single sticker are usually one-off faces; they're kept but not listed. */
        private const val MIN_STICKERS = 2
        private const val MAX_FACES = 300

        val Factory = viewModelFactory {
            initializer { PeopleViewModel(this[APPLICATION_KEY] as StickerFinderApp) }
        }
    }
}
