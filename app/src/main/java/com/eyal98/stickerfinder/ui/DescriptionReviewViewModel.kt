package com.eyal98.stickerfinder.ui

import android.content.Context
import androidx.core.content.edit
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.eyal98.stickerfinder.StickerFinderApp
import com.eyal98.stickerfinder.caption.CaptionPrompt
import com.eyal98.stickerfinder.data.StickerEntity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** The user's judgement of one description. */
enum class Verdict { GOOD, GENERIC, WRONG }

data class DescriptionReviewState(
    val loading: Boolean = true,
    val stickers: List<StickerEntity> = emptyList(),
    val index: Int = 0,
    val verdicts: Map<Long, Verdict> = emptyMap(),
) {
    val current: StickerEntity? get() = stickers.getOrNull(index)
    val finished: Boolean get() = !loading && index >= stickers.size
}

/**
 * "Check descriptions": shows a random sample of described stickers, newest prompt first, and
 * asks whether each description is good, too generic, or wrong. The tally stays on the phone
 * (and in the diagnostics report as counts); the report the user can share holds the
 * descriptions they marked, never images.
 */
class DescriptionReviewViewModel(private val app: StickerFinderApp) : ViewModel() {

    private val _state = MutableStateFlow(DescriptionReviewState())
    val state: StateFlow<DescriptionReviewState> = _state.asStateFlow()

    init {
        restart()
    }

    fun restart() {
        _state.value = DescriptionReviewState()
        viewModelScope.launch {
            val sample = app.database.stickerDao().describedSample("+p${CaptionPrompt.VERSION}", SAMPLE_SIZE)
            _state.value = DescriptionReviewState(loading = false, stickers = sample)
        }
    }

    fun judge(verdict: Verdict) {
        val sticker = _state.value.current ?: return
        DescriptionReviewStore.record(app, verdict)
        _state.update { it.copy(verdicts = it.verdicts + (sticker.id to verdict), index = it.index + 1) }
    }

    fun skip() = _state.update { it.copy(index = it.index + 1) }

    /** Plain text for the share sheet: the tally, and what the model wrote for the misses. */
    fun report(): String {
        val s = _state.value
        val byId = s.stickers.associateBy { it.id }
        return buildString {
            appendLine("Description check (${s.verdicts.size} stickers, prompt v${CaptionPrompt.VERSION})")
            for (v in Verdict.entries) appendLine("${v.name.lowercase()}: ${s.verdicts.values.count { it == v }}")
            for ((id, verdict) in s.verdicts) {
                if (verdict == Verdict.GOOD) continue
                val st = byId[id] ?: continue
                appendLine()
                appendLine("[${verdict.name.lowercase()}] ${st.captionModel ?: "?"}")
                st.captionEn?.let { appendLine("EN: $it") }
                st.captionHe?.let { appendLine("HE: $it") }
                st.captionTags?.let { appendLine("TAGS: $it") }
                st.imageTags?.let { appendLine("picture tags: $it") }
                st.packName?.let { appendLine("pack: $it") }
                if (st.captionEn == null && st.captionHe == null && st.captionTags == null) appendLine("(no description)")
            }
        }
    }

    companion object {
        private const val SAMPLE_SIZE = 30

        val Factory = viewModelFactory {
            initializer { DescriptionReviewViewModel(this[APPLICATION_KEY] as StickerFinderApp) }
        }
    }
}

/** Running totals of the user's verdicts, for the diagnostics report. */
object DescriptionReviewStore {
    private const val PREFS = "description_review"

    fun record(context: Context, verdict: Verdict) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs.edit { putInt(key(verdict), prefs.getInt(key(verdict), 0) + 1) }
    }

    /** "good 12, generic 5, wrong 3", or null before the first check. */
    fun describe(context: Context): String? {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val counts = Verdict.entries.associateWith { prefs.getInt(key(it), 0) }
        if (counts.values.sum() == 0) return null
        return "description check (prompt v${CaptionPrompt.VERSION}): " +
            counts.entries.joinToString(", ") { "${it.key.name.lowercase()} ${it.value}" }
    }

    // Per prompt version, so a new prompt starts a fresh tally.
    private fun key(verdict: Verdict) = "v${CaptionPrompt.VERSION}_${verdict.name}"
}
