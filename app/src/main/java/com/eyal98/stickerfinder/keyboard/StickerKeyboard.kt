package com.eyal98.stickerfinder.keyboard

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.eyal98.stickerfinder.R
import com.eyal98.stickerfinder.data.StickerEntity
import com.eyal98.stickerfinder.ui.StickerThumbnail

enum class KeyboardMessage { SENT, NOT_ACCEPTED, FAILED }

data class KeyboardUiState(
    val query: String = "",
    val results: List<StickerEntity> = emptyList(),
    val loading: Boolean = false,
    /** Whether the text box accepts inserted images at all. */
    val canSend: Boolean = true,
    /** The type stickers are sent as here; shown so it's clear whether they arrive as stickers. */
    val sendMimeType: String? = null,
    val message: KeyboardMessage? = null,
)

@Composable
fun StickerKeyboard(
    state: KeyboardUiState,
    onSend: (StickerEntity) -> Unit,
    onBrowse: () -> Unit,
    onSwitchKeyboard: () -> Unit,
) {
    Surface(tonalElevation = 3.dp) {
        Column(
            Modifier
                .fillMaxWidth()
                .height(KEYBOARD_HEIGHT),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 4.dp)) {
                TextButton(onClick = onSwitchKeyboard) { Text("⌨") }
                Text(
                    text = if (state.query.isBlank()) {
                        stringResource(R.string.keyboard_hint)
                    } else {
                        stringResource(R.string.keyboard_results_for, state.query)
                    },
                    style = MaterialTheme.typography.labelLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                if (state.query.isNotBlank()) {
                    TextButton(onClick = onBrowse) { Text(stringResource(R.string.keyboard_browse)) }
                }
            }
            Box(Modifier.weight(1f)) {
                when {
                    !state.canSend -> Centered(stringResource(R.string.keyboard_not_accepted))
                    state.results.isEmpty() && !state.loading -> Centered(stringResource(R.string.keyboard_no_results))
                    else -> LazyVerticalGrid(
                        columns = GridCells.Adaptive(72.dp),
                        contentPadding = PaddingValues(4.dp),
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        items(state.results, key = { it.id }) { sticker ->
                            StickerThumbnail(
                                documentUri = sticker.documentUri,
                                contentDescription = sticker.userTags.ifBlank { sticker.captionHe ?: sticker.captionEn ?: sticker.ocrText },
                                modifier = Modifier
                                    .padding(4.dp)
                                    .aspectRatio(1f)
                                    .clickable { onSend(sticker) },
                            )
                        }
                    }
                }
            }
            Text(
                text = when (state.message) {
                    KeyboardMessage.SENT -> stringResource(R.string.keyboard_sent, state.sendMimeType.orEmpty())
                    KeyboardMessage.NOT_ACCEPTED -> stringResource(R.string.keyboard_not_accepted)
                    KeyboardMessage.FAILED -> stringResource(R.string.keyboard_failed)
                    null -> state.sendMimeType?.let { stringResource(R.string.keyboard_sends_as, it) }.orEmpty()
                },
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp),
            )
        }
    }
}

@Composable
private fun Centered(text: String) {
    Box(Modifier.fillMaxSize().padding(16.dp), contentAlignment = Alignment.Center) {
        Text(text, style = MaterialTheme.typography.bodyMedium)
    }
}

private val KEYBOARD_HEIGHT = 300.dp
