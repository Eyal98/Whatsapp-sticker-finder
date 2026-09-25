package com.eyal98.stickerfinder.keyboard

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyHorizontalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.eyal98.stickerfinder.R
import com.eyal98.stickerfinder.data.StickerEntity
import com.eyal98.stickerfinder.ui.StickerThumbnail

enum class KeyboardMessage { NOT_ACCEPTED, FAILED }

enum class KeyLayout(val rows: List<String>, val label: String) {
    HEBREW(listOf("קראטוןםפ", "שדגכעיחלךף", "זסבהנמצתץ"), "עב"),
    ENGLISH(listOf("qwertyuiop", "asdfghjkl", "zxcvbnm"), "EN"),
}

data class KeyboardUiState(
    val query: String = "",
    val results: List<StickerEntity> = emptyList(),
    val loading: Boolean = false,
    val layout: KeyLayout = KeyLayout.HEBREW,
    /** Whether the text box accepts inserted images at all. */
    val canSend: Boolean = true,
    val message: KeyboardMessage? = null,
)

interface KeyboardActions {
    fun onKey(char: Char)
    fun onBackspace()
    fun onClear()
    fun onToggleLayout()
    fun onSend(sticker: StickerEntity)
    fun onSwitchKeyboard()
}

/**
 * Search bar, a strip of results, and letter keys for typing the search right here, in Hebrew or
 * English, without switching back to another keyboard first.
 */
@Composable
fun StickerKeyboard(state: KeyboardUiState, actions: KeyboardActions) {
    Surface(tonalElevation = 3.dp) {
        Column(Modifier.fillMaxWidth().padding(bottom = 4.dp)) {
            SearchBar(state, actions)
            Box(Modifier.fillMaxWidth().height(RESULTS_HEIGHT)) {
                when {
                    !state.canSend -> Centered(stringResource(R.string.keyboard_not_accepted))
                    state.message == KeyboardMessage.FAILED -> Centered(stringResource(R.string.keyboard_failed))
                    state.results.isEmpty() && !state.loading -> Centered(stringResource(R.string.keyboard_no_results))
                    else -> LazyHorizontalGrid(
                        rows = GridCells.Fixed(2),
                        contentPadding = PaddingValues(horizontal = 4.dp),
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        items(state.results, key = { it.id }) { sticker ->
                            StickerThumbnail(
                                documentUri = sticker.documentUri,
                                contentDescription = sticker.userTags.ifBlank { sticker.captionHe ?: sticker.captionEn ?: sticker.ocrText },
                                modifier = Modifier
                                    .padding(3.dp)
                                    .fillMaxHeight()
                                    .aspectRatio(1f)
                                    .clickable { actions.onSend(sticker) },
                            )
                        }
                    }
                }
            }
            Keys(state.layout, actions)
        }
    }
}

@Composable
private fun SearchBar(state: KeyboardUiState, actions: KeyboardActions) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 4.dp)) {
        TextButton(onClick = actions::onSwitchKeyboard) { Text("⌨") }
        Text(
            text = state.query.ifEmpty { stringResource(R.string.keyboard_hint) },
            style = if (state.query.isEmpty()) MaterialTheme.typography.bodyMedium else MaterialTheme.typography.titleMedium,
            color = if (state.query.isEmpty()) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (state.query.isNotEmpty()) TextButton(onClick = actions::onClear) { Text("✕") }
    }
}

@Composable
private fun Keys(layout: KeyLayout, actions: KeyboardActions) {
    // A keyboard's key order is physical; don't mirror it when the phone's language is Hebrew.
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 2.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            for (row in layout.rows) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    for (char in row) Key(char.toString(), Modifier.weight(1f)) { actions.onKey(char) }
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Key(if (layout == KeyLayout.HEBREW) KeyLayout.ENGLISH.label else KeyLayout.HEBREW.label, Modifier.weight(1.5f)) {
                    actions.onToggleLayout()
                }
                Key("␣", Modifier.weight(5f)) { actions.onKey(' ') }
                Key("⌫", Modifier.weight(1.5f), onClick = actions::onBackspace)
            }
        }
    }
}

@Composable
private fun Key(label: String, modifier: Modifier, onClick: () -> Unit) {
    Surface(
        shape = MaterialTheme.shapes.small,
        tonalElevation = 6.dp,
        modifier = modifier.height(KEY_HEIGHT).clickable(onClick = onClick),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(label, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
        }
    }
}

@Composable
private fun Centered(text: String) {
    Box(Modifier.fillMaxSize().padding(16.dp), contentAlignment = Alignment.Center) {
        Text(text, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center)
    }
}

private val RESULTS_HEIGHT = 150.dp
private val KEY_HEIGHT = 42.dp
