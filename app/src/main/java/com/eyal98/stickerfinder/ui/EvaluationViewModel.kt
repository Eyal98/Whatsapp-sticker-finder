package com.eyal98.stickerfinder.ui

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.eyal98.stickerfinder.StickerFinderApp
import com.eyal98.stickerfinder.data.EvaluationReport
import com.eyal98.stickerfinder.data.GoldenSetStore
import com.eyal98.stickerfinder.data.StickerEntity
import com.eyal98.stickerfinder.data.StickerRepository
import com.eyal98.stickerfinder.search.GoldenQuery
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONException
import java.io.IOException
import java.util.UUID

/** A test search being written: its text and the stickers it should find. */
data class EditState(
    val text: String = "",
    val finder: String = "",
    val candidates: List<StickerEntity> = emptyList(),
    /** Selected stickers by image key, in selection order. */
    val selected: Map<String, StickerEntity> = emptyMap(),
) {
    val canSave: Boolean get() = text.isNotBlank() && selected.isNotEmpty()
}

enum class EvaluationMessage { SAVE_FAILED, EXPORTED, EXPORT_FAILED, IMPORTED, IMPORT_FAILED, THRESHOLD_APPLIED }

data class EvaluationUiState(
    val queries: List<GoldenQuery> = emptyList(),
    val editing: EditState? = null,
    /** (done, total) while the test runs. */
    val progress: Pair<Int, Int>? = null,
    val report: EvaluationReport? = null,
    val message: EvaluationMessage? = null,
)

@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
class EvaluationViewModel(private val app: StickerFinderApp) : ViewModel() {

    private val _state = MutableStateFlow(EvaluationUiState())
    val state: StateFlow<EvaluationUiState> = _state.asStateFlow()

    private val finder = MutableStateFlow("")

    init {
        viewModelScope.launch {
            val queries = withContext(Dispatchers.IO) { app.goldenSet.load() }
            _state.update { it.copy(queries = queries) }
        }
        // Stickers to pick from while writing a test search: found with the finder text, or
        // the browse order when it's empty.
        finder.debounce(FINDER_DEBOUNCE_MS)
            .flatMapLatest { q ->
                if (q.isBlank()) {
                    app.repository.browse(PICKER_LIMIT)
                } else {
                    flow { emit(app.repository.search(q)) }
                }
            }
            .map { list -> StickerRepository.dedupe(list) }
            .onEach { list -> _state.update { s -> s.copy(editing = s.editing?.copy(candidates = list)) } }
            .launchIn(viewModelScope)
    }

    fun startAdding() {
        finder.value = ""
        _state.update { it.copy(editing = EditState(), message = null) }
    }

    fun cancelEditing() = _state.update { it.copy(editing = null) }

    fun setText(text: String) = _state.update { it.copy(editing = it.editing?.copy(text = text)) }

    fun setFinder(text: String) {
        finder.value = text
        _state.update { it.copy(editing = it.editing?.copy(finder = text)) }
    }

    fun toggle(sticker: StickerEntity) = _state.update { s ->
        val edit = s.editing ?: return@update s
        val key = StickerRepository.imageKey(sticker)
        val selected = if (key in edit.selected) edit.selected - key else edit.selected + (key to sticker)
        s.copy(editing = edit.copy(selected = selected))
    }

    fun saveEditing() {
        val edit = _state.value.editing ?: return
        if (!edit.canSave) return
        val query = GoldenQuery(UUID.randomUUID().toString().take(8), edit.text.trim(), edit.selected.keys)
        persist(_state.value.queries + query)
        _state.update { it.copy(editing = null) }
    }

    fun delete(query: GoldenQuery) = persist(_state.value.queries - query)

    fun run() {
        if (_state.value.progress != null) return
        val queries = _state.value.queries
        viewModelScope.launch {
            _state.update { it.copy(progress = 0 to queries.size, report = null, message = null) }
            val report = app.evaluator.run(queries) { done, total ->
                _state.update { it.copy(progress = done to total) }
            }
            _state.update { it.copy(progress = null, report = report) }
        }
    }

    fun applyThreshold(value: Float) {
        app.searchSettings.minSimilarity = value
        _state.update { s -> s.copy(report = s.report?.copy(threshold = value), message = EvaluationMessage.THRESHOLD_APPLIED) }
    }

    fun export(uri: Uri) = viewModelScope.launch {
        val ok = withContext(Dispatchers.IO) {
            try {
                app.contentResolver.openOutputStream(uri)?.use { GoldenSetStore.write(_state.value.queries, it) } != null
            } catch (e: IOException) {
                false
            } catch (e: SecurityException) {
                false
            }
        }
        _state.update { it.copy(message = if (ok) EvaluationMessage.EXPORTED else EvaluationMessage.EXPORT_FAILED) }
    }

    /** Adds the imported test searches, skipping ones already present (same text). */
    fun import(uri: Uri) = viewModelScope.launch {
        val imported = withContext(Dispatchers.IO) {
            try {
                app.contentResolver.openInputStream(uri)?.use { GoldenSetStore.read(it) }
            } catch (e: IOException) {
                null
            } catch (e: JSONException) {
                null
            } catch (e: SecurityException) {
                null
            }
        }
        if (imported == null) {
            _state.update { it.copy(message = EvaluationMessage.IMPORT_FAILED) }
            return@launch
        }
        val existing = _state.value.queries
        val known = existing.mapTo(HashSet()) { it.text }
        persist(existing + imported.filter { it.text !in known })
        _state.update { it.copy(message = EvaluationMessage.IMPORTED) }
    }

    fun clearMessage() = _state.update { it.copy(message = null) }

    private fun persist(queries: List<GoldenQuery>) {
        _state.update { it.copy(queries = queries) }
        viewModelScope.launch {
            val ok = withContext(Dispatchers.IO) {
                try {
                    app.goldenSet.save(queries)
                    true
                } catch (e: IOException) {
                    false
                }
            }
            if (!ok) _state.update { it.copy(message = EvaluationMessage.SAVE_FAILED) }
        }
    }

    companion object {
        private const val FINDER_DEBOUNCE_MS = 200L
        private const val PICKER_LIMIT = 300

        val Factory = viewModelFactory {
            initializer { EvaluationViewModel(this[APPLICATION_KEY] as StickerFinderApp) }
        }
    }
}
