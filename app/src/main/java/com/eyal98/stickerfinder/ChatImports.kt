package com.eyal98.stickerfinder

import android.net.Uri
import android.util.Log
import com.eyal98.stickerfinder.index.ChatImporter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * Runs "Learn from your chats" imports, one at a time, in the app's scope so leaving the screen
 * doesn't stop one. It isn't a background job: the export's content URI is only readable while
 * the app holds the grant it came with, and handing the work to a job would mean copying the chat
 * to storage, which it must never be.
 */
class ChatImports(private val app: StickerFinderApp, private val scope: CoroutineScope) {

    sealed interface State {
        data object Idle : State
        data class Running(val progress: ChatImporter.Progress?) : State
        data class Done(val outcome: ChatImporter.Outcome) : State
        data object Failed : State
    }

    private val _state = MutableStateFlow<State>(State.Idle)
    val state: StateFlow<State> = _state

    private val _offered = MutableStateFlow<Uri?>(null)

    /** A shared or picked export waiting for the user's Import. Nothing is read from it until then. */
    val offered: StateFlow<Uri?> = _offered

    fun offer(uri: Uri) {
        _offered.value = uri
    }

    fun cancelOffer() {
        _offered.value = null
    }

    /** Starts the offered import; the offer stays while another import runs. */
    fun acceptOffer() {
        val uri = _offered.value ?: return
        if (_state.value is State.Running) return
        _offered.value = null
        start(uri)
    }

    /** Starts importing [uri]; ignored while another import runs. */
    private fun start(uri: Uri) {
        synchronized(this) {
            if (_state.value is State.Running) return
            _state.value = State.Running(null)
        }
        scope.launch(Dispatchers.IO) {
            _state.value = try {
                val importer = ChatImporter(app.contentResolver, app.database.stickerDao(), app.embedders)
                State.Done(importer.import(uri) { _state.value = State.Running(it) })
            } catch (e: Exception) {
                // The type only: a message could name the file.
                Log.w(TAG, "Chat import failed: ${e.javaClass.simpleName}")
                State.Failed
            }
        }
    }

    /** Deletes everything learned from chats, and which chats were imported. */
    fun forget() {
        if (_state.value is State.Running) return
        scope.launch(Dispatchers.IO) {
            app.database.stickerDao().forgetChats()
            _state.value = State.Idle
        }
    }

    private companion object {
        const val TAG = "ChatImports"
    }
}
