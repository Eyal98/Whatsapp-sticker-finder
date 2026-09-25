package com.eyal98.stickerfinder.keyboard

import android.inputmethodservice.InputMethodService
import android.net.Uri
import android.text.InputType
import android.view.View
import android.view.inputmethod.EditorInfo
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.ComposeView
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
import com.eyal98.stickerfinder.ui.StickerFinderTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * A keyboard that only inserts stickers. Type what you want in the app's own text box with your
 * usual keyboard, switch to this one, and tap a result: the sticker is sent and the search text
 * removed.
 *
 * Privacy: it reads at most [MAX_QUERY_CHARS] characters before the cursor, only when it opens,
 * never from password fields, and never stores them. The app has no network access.
 */
class StickerKeyboardService : InputMethodService(), LifecycleOwner, ViewModelStoreOwner, SavedStateRegistryOwner {

    private val lifecycleRegistry = LifecycleRegistry(this)
    private val savedStateController = SavedStateRegistryController.create(this)
    private val store = ViewModelStore()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var searchJob: Job? = null

    override val lifecycle: Lifecycle get() = lifecycleRegistry
    override val viewModelStore: ViewModelStore get() = store
    override val savedStateRegistry: SavedStateRegistry get() = savedStateController.savedStateRegistry

    private val state = mutableStateOf(KeyboardUiState())

    private val app get() = application as StickerFinderApp

    override fun onCreate() {
        super.onCreate()
        savedStateController.performRestore(null)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
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
                    StickerKeyboard(
                        state = state.value,
                        onSend = ::send,
                        onBrowse = { search("") },
                        onSwitchKeyboard = { switchToPreviousInputMethod() },
                    )
                }
            }
        }
    }

    override fun onStartInputView(info: EditorInfo, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
        val query = if (isPassword(info)) "" else currentQuery()
        state.value = KeyboardUiState(
            canSend = StickerSender.chooseMimeType(info) != null,
            sendMimeType = StickerSender.chooseMimeType(info),
        )
        search(query)
    }

    override fun onFinishInputView(finishingInput: Boolean) {
        super.onFinishInputView(finishingInput)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
    }

    override fun onDestroy() {
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        scope.cancel()
        store.clear()
        super.onDestroy()
    }

    /** The text being typed: what's before the cursor on the current line. */
    private fun currentQuery(): String =
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

    private fun search(query: String) {
        searchJob?.cancel()
        state.value = state.value.copy(query = query, loading = true, message = null)
        searchJob = scope.launch {
            val results = if (query.isBlank()) {
                app.repository.browse(BROWSE_LIMIT).first()
            } else {
                // Keyword results first (instant), then the merged ranking once it's ready.
                state.value = state.value.copy(results = app.repository.searchKeywords(query))
                app.repository.search(query)
            }
            state.value = state.value.copy(results = results, loading = false)
        }
    }

    private fun send(sticker: StickerEntity) {
        val info = currentInputEditorInfo ?: return
        val connection = currentInputConnection ?: return
        val query = state.value.query
        val mimeType = StickerSender.chooseMimeType(info)
        if (mimeType == null) {
            state.value = state.value.copy(message = KeyboardMessage.NOT_ACCEPTED)
            return
        }
        scope.launch {
            val prepared = withContext(Dispatchers.IO) {
                StickerSender.prepare(this@StickerKeyboardService, mimeType, Uri.parse(sticker.documentUri))
            }
            val result = prepared?.let { StickerSender.commit(info, connection, it) } ?: StickerSender.Result.Failed
            when (result) {
                is StickerSender.Result.Sent -> {
                    removeQueryText(query)
                    app.repository.recordUse(sticker.id)
                    state.value = state.value.copy(message = KeyboardMessage.SENT, sendMimeType = result.mimeType)
                }
                StickerSender.Result.NotAccepted -> state.value = state.value.copy(message = KeyboardMessage.NOT_ACCEPTED)
                StickerSender.Result.Failed -> state.value = state.value.copy(message = KeyboardMessage.FAILED)
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
    }
}
