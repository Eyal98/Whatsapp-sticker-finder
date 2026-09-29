package com.eyal98.stickerfinder.keyboard

import android.content.Context
import android.inputmethodservice.InputMethodService
import android.net.Uri
import android.text.InputType
import android.view.View
import android.view.inputmethod.EditorInfo
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.ComposeView
import androidx.core.content.edit
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.eyal98.stickerfinder.StickerFinderApp
import com.eyal98.stickerfinder.data.StickerEntity
import com.eyal98.stickerfinder.index.EmbedWorker
import com.eyal98.stickerfinder.search.QueryProvenance
import com.eyal98.stickerfinder.search.TextNormalizer
import com.eyal98.stickerfinder.ui.Onboarding
import com.eyal98.stickerfinder.ui.StickerFinderTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * A keyboard that only sends stickers. Switch to it, type a few letters on its own Hebrew or
 * English keys (or use what's already typed in the text box), tap a sticker: it's sent as a real
 * sticker and your usual keyboard comes back.
 *
 * Privacy: typing on its keys stays inside the keyboard. It reads at most [MAX_QUERY_CHARS]
 * characters already in the text box, only when it opens, never from password fields, and never
 * stores them. The app has no network access.
 */
class StickerKeyboardService :
    InputMethodService(),
    LifecycleOwner,
    ViewModelStoreOwner,
    SavedStateRegistryOwner,
    KeyboardActions {

    private val lifecycleRegistry = LifecycleRegistry(this)
    private val savedStateController = SavedStateRegistryController.create(this)
    private val store = ViewModelStore()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var searchJob: Job? = null

    /** The last sticker sent (its shared copy's name), to note in [SendLog] when the keyboard goes away. */
    private var lastSent: String? = null

    override val lifecycle: Lifecycle get() = lifecycleRegistry
    override val viewModelStore: ViewModelStore get() = store
    override val savedStateRegistry: SavedStateRegistry get() = savedStateController.savedStateRegistry

    private val state = mutableStateOf(KeyboardUiState())

    /** Whether the current search came from the text box (and should be removed after sending). */
    private val provenance = QueryProvenance()

    private val app get() = application as StickerFinderApp
    private val prefs get() = getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    override fun onCreate() {
        super.onCreate()
        savedStateController.performRestore(null)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
        // The folder chips follow the user's folders; a deleted folder can't stay selected.
        scope.launch {
            app.repository.folders.collect { folders ->
                val selected = state.value.selectedFolder?.takeIf { id -> folders.any { it.id == id } }
                val changed = selected != state.value.selectedFolder
                state.value = state.value.copy(folders = folders, selectedFolder = selected)
                if (changed) search(immediately = true)
            }
        }
    }

    override fun onCreateInputView(): View {
        // Compose finds its lifecycle and saved state through the window's view tree.
        window?.window?.decorView?.let { decor ->
            decor.setViewTreeLifecycleOwner(this)
            decor.setViewTreeViewModelStoreOwner(this)
            decor.setViewTreeSavedStateRegistryOwner(this)
        }
        return ComposeView(this).apply {
            setViewTreeLifecycleOwner(this@StickerKeyboardService)
            setViewTreeViewModelStoreOwner(this@StickerKeyboardService)
            setViewTreeSavedStateRegistryOwner(this@StickerKeyboardService)
            setContent {
                StickerFinderTheme {
                    StickerKeyboard(state.value, this@StickerKeyboardService)
                }
            }
        }
    }

    override fun onStartInputView(info: EditorInfo, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
        val fieldText = if (isPassword(info)) "" else currentFieldText()
        provenance.opened(fieldText)
        val savedLayout = prefs.getString(KEY_LAYOUT, null)?.let { name -> KeyLayout.entries.firstOrNull { it.name == name } }
        state.value = KeyboardUiState(
            query = fieldText,
            layout = when {
                fieldText.any(TextNormalizer::isHebrewLetter) -> KeyLayout.HEBREW
                fieldText.any { it in 'a'..'z' || it in 'A'..'Z' } -> KeyLayout.ENGLISH
                else -> savedLayout ?: KeyLayout.HEBREW
            },
            canSend = StickerSender.chooseMimeType(info) != null,
            // The folder browsed last time stays open.
            folders = state.value.folders,
            selectedFolder = state.value.selectedFolder,
        )
        search(immediately = true)
    }

    override fun onFinishInputView(finishingInput: Boolean) {
        super.onFinishInputView(finishingInput)
        lastSent?.let { SendLog.keyboardLeft(this, it) }
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
    }

    override fun onDestroy() {
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        scope.cancel()
        store.clear()
        super.onDestroy()
    }

    // Typing on the keyboard's own keys edits the search only, never the text box.

    override fun onKey(char: Char) = editQuery(state.value.query + char)

    override fun onBackspace() = editQuery(state.value.query.dropLast(1))

    override fun onClear() = editQuery("")

    override fun onToggleLayout() {
        val next = if (state.value.layout == KeyLayout.HEBREW) KeyLayout.ENGLISH else KeyLayout.HEBREW
        prefs.edit { putString(KEY_LAYOUT, next.name) }
        state.value = state.value.copy(layout = next)
    }

    override fun onSelectFolder(id: Long?) {
        state.value = state.value.copy(selectedFolder = id, message = null)
        search(immediately = true)
    }

    override fun onSwitchKeyboard() {
        switchToPreviousInputMethod()
    }

    private fun editQuery(query: String) {
        // Once edited here, the search no longer matches the text box, which is then left alone
        // after sending; but chat text in it is still never learned from (see QueryProvenance).
        provenance.edited(query)
        state.value = state.value.copy(query = query, message = null)
        search(immediately = false)
    }

    /** The text being typed in the text box: what's before the cursor on the current line. */
    private fun currentFieldText(): String =
        currentInputConnection?.getTextBeforeCursor(MAX_QUERY_CHARS, 0)
            ?.toString()
            ?.substringAfterLast('\n')
            ?.trim()
            .orEmpty()

    private fun isPassword(info: EditorInfo): Boolean {
        val variation = info.inputType and InputType.TYPE_MASK_VARIATION
        return when (info.inputType and InputType.TYPE_MASK_CLASS) {
            InputType.TYPE_CLASS_TEXT -> variation == InputType.TYPE_TEXT_VARIATION_PASSWORD ||
                variation == InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD ||
                variation == InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD
            InputType.TYPE_CLASS_NUMBER -> variation == InputType.TYPE_NUMBER_VARIATION_PASSWORD
            else -> false
        }
    }

    private fun search(immediately: Boolean) {
        searchJob?.cancel()
        val query = state.value.query
        val folder = state.value.selectedFolder
        state.value = state.value.copy(loading = true)
        searchJob = scope.launch {
            // Wait for a pause in typing before searching.
            if (!immediately) delay(TYPING_PAUSE_MS)
            // In a folder, the search itself only looks at the folder's stickers.
            val results = when {
                query.isBlank() && folder != null -> app.repository.folderStickers(folder).first()
                query.isBlank() -> app.repository.browse(BROWSE_LIMIT).first()
                else -> {
                    // Keyword results first (instant), then the merged ranking once it's ready.
                    state.value = state.value.copy(results = app.repository.searchKeywords(query, folderId = folder))
                    // The meaning search runs the embedding model: only once typing pauses (a new
                    // keystroke cancels this job).
                    if (!immediately) delay(MEANING_PAUSE_MS)
                    app.repository.search(query, folderId = folder)
                }
            }
            state.value = state.value.copy(results = results, loading = false)
        }
    }

    override fun onSend(sticker: StickerEntity) {
        val info = currentInputEditorInfo ?: return
        val connection = currentInputConnection ?: return
        val mimeType = StickerSender.chooseMimeType(info)
        if (mimeType == null) {
            state.value = state.value.copy(message = KeyboardMessage.NOT_ACCEPTED)
            return
        }
        val query = state.value.query
        val removeFromField = provenance.isFieldText
        val learnable = provenance.learnable
        scope.launch {
            val prepared = withContext(Dispatchers.IO) {
                StickerSender.prepare(this@StickerKeyboardService, mimeType, Uri.parse(sticker.documentUri), info.packageName, sticker.isAnimated)
            }
            val result = prepared?.let { StickerSender.commit(info, connection, it) } ?: StickerSender.Result.Failed
            prepared?.let {
                lastSent = it.fileName
                SendLog.committed(this@StickerKeyboardService, it.fileName, result is StickerSender.Result.Sent)
            }
            if (result is StickerSender.Result.Sent) {
                if (removeFromField) removeQueryText(query)
                app.repository.recordUse(sticker.id)
                Onboarding.complete(app, Onboarding.Step.SEND)
                // Search learns from picks, but only for searches typed on this keyboard's keys:
                // text read from the chat box is never saved.
                if (learnable && app.repository.recordPick(query, sticker.id)) EmbedWorker.runForEdit(app)
                if (sticker.isAnimated) {
                    // WhatsApp reads an animated sticker more than once: to send it, then again for
                    // its own copy and preview. Its permission to read the file can end as soon as
                    // this keyboard switches away, and then the sender saw "Couldn't share" and an
                    // empty bubble (while the other side got the sticker). Stay up a moment first.
                    state.value = state.value.copy(message = KeyboardMessage.SENT)
                    delay(ANIMATED_HOLD_MS)
                    state.value = state.value.copy(message = null)
                    // The user may have switched keyboards or left the chat meanwhile.
                    if (!isInputViewShown) return@launch
                }
                // Done: hand the text box back to the usual keyboard.
                switchToPreviousInputMethod()
            } else {
                state.value = state.value.copy(message = KeyboardMessage.FAILED)
            }
        }
    }

    /** Deletes the search text from the text box, if it's still what's before the cursor. */
    private fun removeQueryText(query: String) {
        if (query.isEmpty()) return
        val connection = currentInputConnection ?: return
        val before = connection.getTextBeforeCursor(MAX_QUERY_CHARS, 0)?.toString() ?: return
        val trimmed = before.trimEnd()
        if (trimmed.endsWith(query)) {
            connection.deleteSurroundingText(before.length - trimmed.length + query.length, 0)
        }
    }

    private companion object {
        const val MAX_QUERY_CHARS = 100
        const val BROWSE_LIMIT = 200
        const val ANIMATED_HOLD_MS = 3_000L
        const val TYPING_PAUSE_MS = 250L
        const val MEANING_PAUSE_MS = 250L
        const val PREFS = "sticker_keyboard"
        const val KEY_LAYOUT = "layout"
    }
}
