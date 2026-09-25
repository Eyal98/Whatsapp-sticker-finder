package com.eyal98.stickerfinder.ui

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.eyal98.stickerfinder.StickerFinderApp
import com.eyal98.stickerfinder.caption.DeviceCapability
import com.eyal98.stickerfinder.caption.InstalledModel
import com.eyal98.stickerfinder.caption.ModelStore
import com.eyal98.stickerfinder.caption.PendingModel
import com.eyal98.stickerfinder.index.CaptionWorker
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

sealed interface ImportProblem {
    data class NotEnoughSpace(val neededBytes: Long) : ImportProblem
    data object Failed : ImportProblem
}

data class ModelUiState(
    val installed: InstalledModel? = null,
    val pending: PendingModel? = null,
    /** 0..1 while copying a model file, null otherwise. */
    val importProgress: Float? = null,
    val problem: ImportProblem? = null,
)

data class SmartSearchUiState(
    val model: ModelUiState = ModelUiState(),
    val captionPending: Int = 0,
    val total: Int = 0,
    val enoughMemory: Boolean = true,
)

class SmartSearchViewModel(private val app: StickerFinderApp) : ViewModel() {

    private val modelState = MutableStateFlow(
        ModelUiState(installed = ModelStore.installed(app), pending = ModelStore.pending(app)),
    )
    private var importJob: Job? = null

    val uiState: StateFlow<SmartSearchUiState> =
        combine(modelState, app.repository.captionPendingCount, app.repository.stickerCount) { model, pending, total ->
            SmartSearchUiState(
                model = model,
                captionPending = pending,
                total = total,
                enoughMemory = DeviceCapability.canRun(app, model.installed?.model ?: model.pending?.guessedModel),
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SmartSearchUiState())

    fun import(uri: Uri) {
        if (importJob?.isActive == true) return
        importJob = viewModelScope.launch {
            modelState.update { it.copy(importProgress = 0f, problem = null, pending = null) }
            val result = ModelStore.import(app, uri) { progress ->
                modelState.update { it.copy(importProgress = progress) }
            }
            modelState.update { it.copy(importProgress = null) }
            when (result) {
                is ModelStore.ImportResult.Installed -> onInstalled(result.model)
                is ModelStore.ImportResult.NeedsConfirmation -> modelState.update { it.copy(pending = result.pending) }
                is ModelStore.ImportResult.NotEnoughSpace ->
                    modelState.update { it.copy(problem = ImportProblem.NotEnoughSpace(result.neededBytes)) }
                ModelStore.ImportResult.Failed -> modelState.update { it.copy(problem = ImportProblem.Failed) }
            }
        }
    }

    fun confirmPending() {
        viewModelScope.launch {
            val installed = withContext(Dispatchers.IO) { ModelStore.confirmPending(app) }
            if (installed != null) onInstalled(installed) else modelState.update { it.copy(problem = ImportProblem.Failed) }
        }
    }

    fun discardPending() {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { ModelStore.discardPending(app) }
            modelState.update { it.copy(pending = null) }
        }
    }

    fun removeModel() {
        CaptionWorker.cancel(app)
        viewModelScope.launch {
            withContext(Dispatchers.IO) { ModelStore.remove(app) }
            modelState.update { it.copy(installed = null) }
        }
    }

    fun startNow() = CaptionWorker.runNow(app)

    private fun onInstalled(model: InstalledModel) {
        modelState.update { it.copy(installed = model, pending = null, problem = null) }
        CaptionWorker.schedulePeriodic(app)
        CaptionWorker.runNow(app)
    }

    companion object {
        val Factory = viewModelFactory {
            initializer { SmartSearchViewModel(this[APPLICATION_KEY] as StickerFinderApp) }
        }
    }
}
