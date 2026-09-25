package com.eyal98.stickerfinder

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.eyal98.stickerfinder.ml.ModelStore
import com.eyal98.stickerfinder.index.CaptionWorker
import com.eyal98.stickerfinder.index.IndexWorker
import com.eyal98.stickerfinder.index.StickerFolder
import com.eyal98.stickerfinder.ui.OnboardingScreen
import com.eyal98.stickerfinder.ui.SearchScreen
import com.eyal98.stickerfinder.ui.SmartSearchScreen
import com.eyal98.stickerfinder.ui.StickerFinderTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val hadFolder = StickerFolder.current(this) != null
        if (hadFolder) startIndexing()

        setContent {
            StickerFinderTheme {
                var hasFolder by rememberSaveable { mutableStateOf(hadFolder) }
                var showSmartSearch by rememberSaveable { mutableStateOf(false) }
                if (hasFolder && showSmartSearch) {
                    SmartSearchScreen(onBack = { showSmartSearch = false })
                } else if (hasFolder) {
                    SearchScreen(onOpenSmartSearch = { showSmartSearch = true })
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
        IndexWorker.runNow(this)
        IndexWorker.schedulePeriodic(this)
        if (ModelStore.CAPTION.installed(this) != null) CaptionWorker.schedulePeriodic(this)
    }
}
