package com.eyal98.stickerfinder

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.lifecycle.lifecycleScope
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.eyal98.stickerfinder.index.ImageTagWorker
import com.eyal98.stickerfinder.index.Power
import com.eyal98.stickerfinder.index.IndexWorker
import com.eyal98.stickerfinder.index.StickerFolder
import com.eyal98.stickerfinder.ui.EvaluationScreen
import com.eyal98.stickerfinder.ui.KeyboardSetupScreen
import com.eyal98.stickerfinder.ui.OnboardingScreen
import com.eyal98.stickerfinder.ui.PeopleScreen
import com.eyal98.stickerfinder.ui.SearchScreen
import com.eyal98.stickerfinder.ui.SmartSearchScreen
import com.eyal98.stickerfinder.ui.StickerDetailsScreen
import com.eyal98.stickerfinder.ui.StickerFinderTheme
import kotlinx.coroutines.launch

private enum class Screen { SEARCH, SMART_SEARCH, QUALITY_TEST, PEOPLE, KEYBOARD }

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val hadFolder = StickerFolder.current(this) != null
        if (hadFolder) startIndexing()

        setContent {
            StickerFinderTheme {
                var hasFolder by rememberSaveable { mutableStateOf(hadFolder) }
                var screen by rememberSaveable { mutableStateOf(Screen.SEARCH) }
                // A sticker's details, opened from search with a long press.
                var details by rememberSaveable { mutableStateOf<Long?>(null) }
                val openDetails = details
                if (hasFolder && openDetails != null) {
                    StickerDetailsScreen(openDetails, onBack = { details = null })
                } else if (hasFolder) {
                    when (screen) {
                        Screen.SEARCH -> SearchScreen(
                            onOpenSmartSearch = { screen = Screen.SMART_SEARCH },
                            onOpenKeyboard = { screen = Screen.KEYBOARD },
                            onOpenDetails = { details = it },
                        )
                        Screen.SMART_SEARCH -> SmartSearchScreen(
                            onBack = { screen = Screen.SEARCH },
                            onOpenQualityTest = { screen = Screen.QUALITY_TEST },
                            onOpenPeople = { screen = Screen.PEOPLE },
                        )
                        Screen.PEOPLE -> PeopleScreen(onBack = { screen = Screen.SMART_SEARCH })
                        Screen.QUALITY_TEST -> EvaluationScreen(onBack = { screen = Screen.SMART_SEARCH })
                        Screen.KEYBOARD -> KeyboardSetupScreen(onBack = { screen = Screen.SEARCH })
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
