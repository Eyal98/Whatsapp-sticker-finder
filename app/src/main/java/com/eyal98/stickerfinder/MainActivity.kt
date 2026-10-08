package com.eyal98.stickerfinder

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.lifecycle.lifecycleScope
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.eyal98.stickerfinder.index.ImageTagWorker
import com.eyal98.stickerfinder.index.Power
import com.eyal98.stickerfinder.index.IndexWorker
import com.eyal98.stickerfinder.index.StickerFolder
import com.eyal98.stickerfinder.ui.AboutScreen
import com.eyal98.stickerfinder.ui.EvaluationScreen
import com.eyal98.stickerfinder.ui.KeyboardSetupScreen
import com.eyal98.stickerfinder.ui.OnboardingScreen
import com.eyal98.stickerfinder.ui.PeopleScreen
import com.eyal98.stickerfinder.ui.SearchScreen
import com.eyal98.stickerfinder.ui.SmartSearchScreen
import com.eyal98.stickerfinder.ui.StickerDetailsScreen
import com.eyal98.stickerfinder.ui.StickerFinderTheme
import com.eyal98.stickerfinder.ui.SuggestionsScreen
import kotlinx.coroutines.launch

private enum class Screen { SEARCH, SMART_SEARCH, QUALITY_TEST, PEOPLE, KEYBOARD, ABOUT, SUGGESTIONS }

class MainActivity : ComponentActivity() {

    /** Set when a chat export is shared to the app: Smart search shows its import. */
    private val openSmartSearch = mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val hadFolder = StickerFolder.current(this) != null
        if (hadFolder) startIndexing()
        // Only the first time: after a rotation the same intent comes back.
        if (savedInstanceState == null) importSharedChat(intent)

        setContent {
            StickerFinderTheme {
                var hasFolder by rememberSaveable { mutableStateOf(hadFolder) }
                var screen by rememberSaveable { mutableStateOf(Screen.SEARCH) }
                // A sticker's details, opened from search with a long press.
                var details by rememberSaveable { mutableStateOf<Long?>(null) }
                val sharedChat by openSmartSearch
                LaunchedEffect(sharedChat) {
                    if (sharedChat) {
                        screen = Screen.SMART_SEARCH
                        details = null
                        openSmartSearch.value = false
                    }
                }
                val openDetails = details
                if (hasFolder && openDetails != null) {
                    StickerDetailsScreen(openDetails, onBack = { details = null })
                } else if (hasFolder) {
                    when (screen) {
                        Screen.SEARCH -> SearchScreen(
                            onOpenSmartSearch = { screen = Screen.SMART_SEARCH },
                            onOpenKeyboard = { screen = Screen.KEYBOARD },
                            onOpenDetails = { details = it },
                            onOpenAbout = { screen = Screen.ABOUT },
                            onOpenSuggestions = { screen = Screen.SUGGESTIONS },
                        )
                        Screen.SMART_SEARCH -> SmartSearchScreen(
                            onBack = { screen = Screen.SEARCH },
                            onOpenQualityTest = { screen = Screen.QUALITY_TEST },
                            onOpenPeople = { screen = Screen.PEOPLE },
                            onOpenSuggestions = { screen = Screen.SUGGESTIONS },
                        )
                        Screen.PEOPLE -> PeopleScreen(onBack = { screen = Screen.SMART_SEARCH })
                        Screen.QUALITY_TEST -> EvaluationScreen(onBack = { screen = Screen.SMART_SEARCH })
                        Screen.KEYBOARD -> KeyboardSetupScreen(onBack = { screen = Screen.SEARCH })
                        Screen.ABOUT -> AboutScreen(onBack = { screen = Screen.SEARCH })
                        Screen.SUGGESTIONS -> SuggestionsScreen(onBack = { screen = Screen.SEARCH })
                    }
                } else {
                    OnboardingScreen(
                        onFolderChosen = { treeUri ->
                            StickerFolder.persist(this, treeUri)
                            startIndexing()
                            hasFolder = true
                        },
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        importSharedChat(intent)
    }

    /**
     * A chat export shared from WhatsApp's Export chat: starts learning from it right away, while
     * the app holds the permission to read it that came with the share.
     */
    private fun importSharedChat(intent: Intent?) {
        if (intent?.action != Intent.ACTION_SEND) return
        val uri = if (Build.VERSION.SDK_INT >= 33) {
            intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)
        } ?: return
        // Without a stickers folder there's nothing to recognize the chat's stickers in yet.
        if (StickerFolder.current(this) == null) return
        (application as StickerFinderApp).chatImports.offer(uri)
        openSmartSearch.value = true
    }

    private fun startIndexing() {
        // Opening the app starts (or restarts) indexing in the foreground, where Android lets it
        // run to the end instead of in throttled background slices.
        lifecycleScope.launch {
            IndexWorker.startNow(this@MainActivity)
            // Picture tagging is minutes of full CPU for a backlog: on battery it waits for the
            // charger (the Smart search screen's Start now still runs it right away).
            if (Power.isCharging(this@MainActivity)) {
                ImageTagWorker.startNow(this@MainActivity)
            } else {
                ImageTagWorker.runNow(this@MainActivity)
            }
        }
        IndexWorker.schedulePeriodic(this)
    }
}
