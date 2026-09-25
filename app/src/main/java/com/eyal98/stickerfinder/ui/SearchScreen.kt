package com.eyal98.stickerfinder.ui

import android.net.Uri
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
fun SearchScreen(viewModel: SearchViewModel = viewModel(factory = SearchViewModel.Factory)) {
    val query by viewModel.query.collectAsStateWithLifecycle()
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var editing by remember { mutableStateOf<StickerEntity?>(null) }

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
            Text(
                text = if (state.pending > 0) {
                    stringResource(R.string.status_indexing, state.pending, state.total)
                } else {
                    stringResource(R.string.status_count, state.total)
                },
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
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
                    onEditTags = { editing = it },
                )
            }
        }
    }

    editing?.let { sticker ->
        TagsDialog(
            initial = sticker.userTags,
            onDismiss = { editing = null },
            onSave = {
                viewModel.setTags(sticker, it)
                editing = null
            },
        )
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
                    contentDescription = sticker.userTags.ifBlank { sticker.ocrText },
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

@Composable
private fun TagsDialog(initial: String, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var text by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.tags_title)) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                placeholder = { Text(stringResource(R.string.tags_hint)) },
            )
        },
        confirmButton = { TextButton(onClick = { onSave(text) }) { Text(stringResource(R.string.save)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}
