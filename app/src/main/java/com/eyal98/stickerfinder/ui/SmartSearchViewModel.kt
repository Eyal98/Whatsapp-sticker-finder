package com.eyal98.stickerfinder.ui

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.eyal98.stickerfinder.StickerFinderApp
import com.eyal98.stickerfinder.index.EmbedWorker
import com.eyal98.stickerfinder.index.ImageTagWorker
import com.eyal98.stickerfinder.ml.BundledEmbedding
import com.eyal98.stickerfinder.ml.DeviceCapability
import com.eyal98.stickerfinder.ml.InstalledModel
import com.eyal98.stickerfinder.ml.ModelCatalog
import com.eyal98.stickerfinder.ml.ModelCrashGuard
import com.eyal98.stickerfinder.ml.ModelSpec
import com.eyal98.stickerfinder.ml.ModelStore
import com.eyal98.stickerfinder.ml.PendingModel
import com.eyal98.stickerfinder.vision.SiglipModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * The model files the user can install, each with the catalog entry to recommend. The picture
 * model is bundled in the app; the meaning-search model is too in release builds, and importing
 * one is only needed in a build made without it (or to try a different file).
 */
enum class ModelSlot(val store: ModelStore, val recommended: ModelSpec) {
    EMBEDDING(ModelStore.EMBEDDING, ModelCatalog.GRANITE_EMBEDDING),
}

sealed interface ImportProblem {
    data class NotEnoughSpace(val neededBytes: Long) : ImportProblem
    data class WrongFileType(val expected: Set<String>) : ImportProblem
    data object Failed : ImportProblem
}

data class SlotUiState(
    val installed: InstalledModel? = null,
    /** The installed model is the one bundled in the app: nothing to import or remove. */
    val builtIn: Boolean = false,
    /** The app bundles a model for this slot (it may still be setting it up). */
    val bundled: Boolean = false,
    val pending: PendingModel? = null,
    /** 0..1 while copying a file into this slot, null otherwise. */
    val importProgress: Float? = null,
    val problem: ImportProblem? = null,
)

data class SmartSearchUiState(
    val slots: Map<ModelSlot, SlotUiState> = emptyMap(),
    val vectorCount: Int = 0,
    val total: Int = 0,
    val embeddingMemoryOk: Boolean = true,
    /** Features turned off because their model crashed the app (see ModelCrashGuard). */
    val turnedOff: Set<String> = emptySet(),
    val imageTagPending: Int = 0,
    val imageTagRunning: Boolean = false,
    /** False only in a build made without the bundled model or labels; the section is hidden then. */
    val pictureTagsAvailable: Boolean = false,
) {
    fun slot(slot: ModelSlot) = slots[slot] ?: SlotUiState()
    val importing: Boolean get() = slots.values.any { it.importProgress != null }
    val firstPending: Pair<ModelSlot, PendingModel>? get() =
        ModelSlot.entries.firstNotNullOfOrNull { s -> slots[s]?.pending?.let { s to it } }
}

private data class Background(
    val turnedOff: Set<String>,
    val imageTagPending: Int,
    val imageTagRunning: Boolean,
)

class SmartSearchViewModel(private val app: StickerFinderApp) : ViewModel() {

    private val slotState = MutableStateFlow(ModelSlot.entries.associateWith { currentSlot(it) })
    private var importJob: Job? = null
    private val pictureTagsAvailable =
        SiglipModel.isBundled(app) && app.assets.list("siglip")?.contains("labels.bin") == true
    private val turnedOff = MutableStateFlow(currentTurnedOff())

    private fun currentSlot(slot: ModelSlot): SlotUiState {
        val imported = slot.store.installed(app)
        return when (slot) {
            ModelSlot.EMBEDDING -> {
                val bundled = if (imported == null) BundledEmbedding.installed(app) else null
                SlotUiState(
                    installed = imported ?: bundled,
                    builtIn = bundled != null,
                    bundled = BundledEmbedding.isBundled(app),
                    pending = slot.store.pending(app),
                )
            }
        }
    }

    /**
     * Picks up the bundled model once the app has finished copying it into place (a few seconds
     * after the first start), checking every couple of seconds while it's still missing.
     */
    fun refresh() {
        viewModelScope.launch {
            repeat(REFRESH_TRIES) {
                val fresh = withContext(Dispatchers.IO) { ModelSlot.entries.associateWith { currentSlot(it) } }
                slotState.update { old ->
                    fresh.mapValues { (slot, s) ->
                        val o = old[slot] ?: SlotUiState()
                        s.copy(pending = o.pending, importProgress = o.importProgress, problem = o.problem)
                    }
                }
                if (fresh.values.none { it.bundled && it.installed == null }) return@launch
                delay(2_000)
            }
        }
    }

    private fun currentTurnedOff() = ModelCrashGuard.FEATURES.filterTo(HashSet()) { ModelCrashGuard.isDisabled(app, it) }

    val uiState: StateFlow<SmartSearchUiState> = combine(
        slotState,
        app.repository.vectorCount,
        app.repository.stickerCount,
        combine(
            turnedOff,
            app.database.stickerDao().observeImageTagPendingCount(),
            ImageTagWorker.observeRunning(app),
        ) { off, imagePending, imageRunning -> Background(off, imagePending, imageRunning) },
    ) { slots, vectors, total, background ->
        SmartSearchUiState(
            turnedOff = background.turnedOff,
            imageTagPending = background.imageTagPending,
            imageTagRunning = background.imageTagRunning,
            pictureTagsAvailable = pictureTagsAvailable,
            slots = slots,
            vectorCount = vectors,
            total = total,
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
                ModelSlot.EMBEDDING -> app.embedders.release()
            }
            withContext(Dispatchers.IO) { slot.store.remove(app) }
            // Falls back to the bundled model, if the app has one.
            val now = withContext(Dispatchers.IO) { currentSlot(slot) }
            update(slot) { it.copy(installed = now.installed, builtIn = now.builtIn) }
            turnedOff.value = currentTurnedOff()
        }
    }

    fun startImageTags() {
        viewModelScope.launch { ImageTagWorker.startNow(app) }
    }

    /** Turns a feature back on after it was turned off for crashing the app. */
    fun turnOn(feature: String) {
        ModelCrashGuard.enable(app, feature)
        turnedOff.value = currentTurnedOff()
        when (feature) {
            ModelCrashGuard.EMBEDDING -> viewModelScope.launch { EmbedWorker.runForEdit(app) }
            ModelCrashGuard.IMAGE_TAGS -> startImageTags()
        }
    }

    private fun onInstalled(slot: ModelSlot, model: InstalledModel) {
        update(slot) { it.copy(installed = model, builtIn = false, pending = null, problem = null) }
        turnedOff.value = currentTurnedOff()
        when (slot) {
            ModelSlot.EMBEDDING -> viewModelScope.launch {
                // Drop any model loaded from the previous files, then embed with the new ones.
                app.embedders.release()
                EmbedWorker.runForEdit(app)
            }
        }
    }

    companion object {
        private const val REFRESH_TRIES = 60

        val Factory = viewModelFactory {
            initializer { SmartSearchViewModel(this[APPLICATION_KEY] as StickerFinderApp) }
        }
    }
}
