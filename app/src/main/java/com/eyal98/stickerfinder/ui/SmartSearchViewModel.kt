package com.eyal98.stickerfinder.ui

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.eyal98.stickerfinder.StickerFinderApp
import com.eyal98.stickerfinder.index.CaptionWorker
import com.eyal98.stickerfinder.index.EmbedWorker
import com.eyal98.stickerfinder.ml.DeviceCapability
import com.eyal98.stickerfinder.ml.InstalledModel
import com.eyal98.stickerfinder.ml.ModelCatalog
import com.eyal98.stickerfinder.ml.ModelCrashGuard
import com.eyal98.stickerfinder.ml.ModelSpec
import com.eyal98.stickerfinder.ml.ModelStore
import com.eyal98.stickerfinder.ml.PendingModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** The model files the user can install, each with the catalog entry to recommend. */
enum class ModelSlot(val store: ModelStore, val recommended: ModelSpec) {
    CAPTION(ModelStore.CAPTION, ModelCatalog.GEMMA_3N_E2B),
    EMBEDDING(ModelStore.EMBEDDING, ModelCatalog.EMBEDDING_GEMMA),
    TOKENIZER(ModelStore.EMBEDDING_TOKENIZER, ModelCatalog.EMBEDDING_GEMMA_TOKENIZER),
}

sealed interface ImportProblem {
    data class NotEnoughSpace(val neededBytes: Long) : ImportProblem
    data class WrongFileType(val expected: Set<String>) : ImportProblem
    data object Failed : ImportProblem
}

data class SlotUiState(
    val installed: InstalledModel? = null,
    val pending: PendingModel? = null,
    /** 0..1 while copying a file into this slot, null otherwise. */
    val importProgress: Float? = null,
    val problem: ImportProblem? = null,
)

data class SmartSearchUiState(
    val slots: Map<ModelSlot, SlotUiState> = emptyMap(),
    val captionPending: Int = 0,
    val vectorCount: Int = 0,
    val total: Int = 0,
    val captionMemoryOk: Boolean = true,
    val embeddingMemoryOk: Boolean = true,
    /** Features turned off because their model crashed the app (see ModelCrashGuard). */
    val turnedOff: Set<String> = emptySet(),
) {
    fun slot(slot: ModelSlot) = slots[slot] ?: SlotUiState()
    val importing: Boolean get() = slots.values.any { it.importProgress != null }
    val firstPending: Pair<ModelSlot, PendingModel>? get() =
        ModelSlot.entries.firstNotNullOfOrNull { s -> slots[s]?.pending?.let { s to it } }
}

class SmartSearchViewModel(private val app: StickerFinderApp) : ViewModel() {

    private val slotState = MutableStateFlow(
        ModelSlot.entries.associateWith { SlotUiState(it.store.installed(app), it.store.pending(app)) },
    )
    private var importJob: Job? = null
    private val turnedOff = MutableStateFlow(currentTurnedOff())

    private fun currentTurnedOff() = setOf(ModelCrashGuard.CAPTION, ModelCrashGuard.EMBEDDING)
        .filterTo(HashSet()) { ModelCrashGuard.isDisabled(app, it) }

    val uiState: StateFlow<SmartSearchUiState> = combine(
        slotState,
        app.repository.captionPendingCount,
        app.repository.vectorCount,
        app.repository.stickerCount,
        turnedOff,
    ) { slots, captionPending, vectors, total, off ->
        SmartSearchUiState(
            turnedOff = off,
            slots = slots,
            captionPending = captionPending,
            vectorCount = vectors,
            total = total,
            captionMemoryOk = memoryOk(slots, ModelSlot.CAPTION),
            embeddingMemoryOk = memoryOk(slots, ModelSlot.EMBEDDING),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SmartSearchUiState())

    private fun memoryOk(slots: Map<ModelSlot, SlotUiState>, slot: ModelSlot): Boolean {
        val s = slots[slot]
        return DeviceCapability.canRun(app, s?.installed?.model ?: s?.pending?.guessedModel, slot.recommended)
    }

    private fun update(slot: ModelSlot, change: (SlotUiState) -> SlotUiState) =
        slotState.update { it + (slot to change(it[slot] ?: SlotUiState())) }

    fun import(slot: ModelSlot, uri: Uri) {
        if (importJob?.isActive == true) return
        importJob = viewModelScope.launch {
            update(slot) { it.copy(importProgress = 0f, problem = null, pending = null) }
            val result = slot.store.import(app, uri) { progress -> update(slot) { it.copy(importProgress = progress) } }
            update(slot) { it.copy(importProgress = null) }
            when (result) {
                is ModelStore.ImportResult.Installed -> onInstalled(slot, result.model)
                is ModelStore.ImportResult.NeedsConfirmation -> update(slot) { it.copy(pending = result.pending) }
                is ModelStore.ImportResult.NotEnoughSpace ->
                    update(slot) { it.copy(problem = ImportProblem.NotEnoughSpace(result.neededBytes)) }
                is ModelStore.ImportResult.WrongFileType ->
                    update(slot) { it.copy(problem = ImportProblem.WrongFileType(result.expected)) }
                ModelStore.ImportResult.Failed -> update(slot) { it.copy(problem = ImportProblem.Failed) }
            }
        }
    }

    fun confirmPending(slot: ModelSlot) {
        viewModelScope.launch {
            val installed = withContext(Dispatchers.IO) { slot.store.confirmPending(app) }
            if (installed != null) onInstalled(slot, installed) else update(slot) { it.copy(problem = ImportProblem.Failed) }
        }
    }

    fun discardPending(slot: ModelSlot) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { slot.store.discardPending(app) }
            update(slot) { it.copy(pending = null) }
        }
    }

    fun remove(slot: ModelSlot) {
        viewModelScope.launch {
            when (slot) {
                ModelSlot.CAPTION -> CaptionWorker.cancel(app)
                ModelSlot.EMBEDDING, ModelSlot.TOKENIZER -> app.embedders.release()
            }
            withContext(Dispatchers.IO) { slot.store.remove(app) }
            update(slot) { it.copy(installed = null) }
            turnedOff.value = currentTurnedOff()
        }
    }

    fun startCaptioning() = CaptionWorker.runNow(app)

    /** Turns a feature back on after it was turned off for crashing the app. */
    fun turnOn(feature: String) {
        ModelCrashGuard.enable(app, feature)
        turnedOff.value = currentTurnedOff()
        when (feature) {
            ModelCrashGuard.CAPTION -> CaptionWorker.runNow(app)
            ModelCrashGuard.EMBEDDING -> EmbedWorker.runNow(app)
        }
    }

    private fun onInstalled(slot: ModelSlot, model: InstalledModel) {
        update(slot) { it.copy(installed = model, pending = null, problem = null) }
        turnedOff.value = currentTurnedOff()
        when (slot) {
            ModelSlot.CAPTION -> {
                CaptionWorker.schedulePeriodic(app)
                CaptionWorker.runNow(app)
            }
            ModelSlot.EMBEDDING, ModelSlot.TOKENIZER -> viewModelScope.launch {
                // Drop any model loaded from the previous files, then embed with the new ones.
                app.embedders.release()
                EmbedWorker.runNow(app)
            }
        }
    }

    companion object {
        val Factory = viewModelFactory {
            initializer { SmartSearchViewModel(this[APPLICATION_KEY] as StickerFinderApp) }
        }
    }
}
