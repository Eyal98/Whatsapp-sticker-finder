package com.eyal98.stickerfinder.ui

import android.net.Uri
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.eyal98.stickerfinder.R
import com.eyal98.stickerfinder.WhatsAppSender
import com.eyal98.stickerfinder.data.StickerEntity

@Composable
fun SearchScreen(
    onOpenSmartSearch: () -> Unit,
    onOpenKeyboard: () -> Unit,
    onOpenDetails: (Long) -> Unit,
    viewModel: SearchViewModel = viewModel(factory = SearchViewModel.Factory),
) {
    val query by viewModel.query.collectAsStateWithLifecycle()
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    // Back from a sticker's details: run the search again so edits show.
    LaunchedEffect(Unit) { viewModel.refresh() }

    Scaffold { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            OutlinedTextField(
                value = query,
                onValueChange = viewModel::onQueryChange,
                placeholder = { Text(stringResource(R.string.search_hint)) },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(start = 16.dp, end = 8.dp),
            ) {
                Text(
                    text = if (state.pending > 0) {
                        stringResource(R.string.status_indexing, state.pending, state.total)
                    } else {
                        stringResource(R.string.status_count, state.total)
                    },
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = onOpenKeyboard) { Text(stringResource(R.string.keyboard_open)) }
                TextButton(onClick = onOpenSmartSearch) { Text(stringResource(R.string.smart_search)) }
            }
            when {
                state.total == 0 && state.pending == 0 -> Message(stringResource(R.string.empty_folder))
                state.results.isEmpty() && !state.isQueryBlank -> Message(stringResource(R.string.no_results))
                else -> StickerGrid(
                    stickers = state.results,
                    onSend = {
                        WhatsAppSender.send(context, Uri.parse(it.documentUri))
                        viewModel.onSent(it)
                    },
                    onToggleStar = viewModel::toggleStar,
                    onEditTags = { onOpenDetails(it.id) },
                )
            }
        }
    }
}

@Composable
private fun Message(text: String) {
    Text(text, modifier = Modifier.padding(24.dp), style = MaterialTheme.typography.bodyLarge)
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun StickerGrid(
    stickers: List<StickerEntity>,
    onSend: (StickerEntity) -> Unit,
    onToggleStar: (StickerEntity) -> Unit,
    onEditTags: (StickerEntity) -> Unit,
) {
    val starDescription = stringResource(R.string.star_description)
    LazyVerticalGrid(
        columns = GridCells.Adaptive(96.dp),
        contentPadding = PaddingValues(8.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        items(stickers, key = { it.id }) { sticker ->
            Box(
                Modifier
                    .aspectRatio(1f)
                    .padding(4.dp)
                    .combinedClickable(
                        onClick = { onSend(sticker) },
                        onLongClick = { onEditTags(sticker) },
                    ),
            ) {
                StickerThumbnail(
                    documentUri = sticker.documentUri,
                    contentDescription = sticker.userTags.ifBlank { sticker.captionHe ?: sticker.captionEn ?: sticker.ocrText },
                    modifier = Modifier.fillMaxSize(),
                )
                TextButton(
                    onClick = { onToggleStar(sticker) },
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .semantics { contentDescription = starDescription },
                ) {
                    Text(if (sticker.starred) "★" else "☆", style = MaterialTheme.typography.titleMedium)
                }
            }
        }
    }
}
