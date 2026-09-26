package com.eyal98.stickerfinder.ui

import android.net.Uri
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
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
    viewModel: SearchViewModel = viewModel(factory = SearchViewModel.Factory),
) {
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
                    onEditTags = { editing = it },
                )
            }
        }
    }

    val lookAlikes by viewModel.lookAlikes.collectAsStateWithLifecycle()
    lookAlikes?.let { offer ->
        LookAlikesDialog(
            offer = offer,
            onToggle = viewModel::toggleLookAlike,
            onApply = viewModel::applyLookAlikes,
            onDismiss = viewModel::dismissLookAlikes,
        )
    }

    editing?.let { sticker ->
        val packSize by produceState(0, sticker.id) { value = sticker.packName?.let { viewModel.packSize(it) } ?: 0 }
        TagsDialog(
            initial = sticker.userTags,
            description = listOfNotNull(sticker.captionHe, sticker.captionEn).joinToString("\n").ifBlank { null },
            packName = sticker.packName.takeIf { packSize > 1 },
            packSize = packSize,
            onDismiss = { editing = null },
            onSave = { tags, wholePack ->
                viewModel.setTags(sticker, tags, wholePack)
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

@Composable
private fun TagsDialog(
    initial: String,
    description: String?,
    /** Set when other stickers share this one's pack: offers to tag them all. */
    packName: String?,
    packSize: Int,
    onDismiss: () -> Unit,
    onSave: (tags: String, wholePack: Boolean) -> Unit,
) {
    var text by remember { mutableStateOf(initial) }
    var wholePack by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.tags_title)) },
        text = {
            Column {
                if (description != null) {
                    Text(stringResource(R.string.description_label), style = MaterialTheme.typography.labelMedium)
                    Text(description, modifier = Modifier.padding(bottom = 12.dp))
                }
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    placeholder = { Text(stringResource(R.string.tags_hint)) },
                )
                if (packName != null) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(top = 8.dp).clickable { wholePack = !wholePack },
                    ) {
                        Checkbox(checked = wholePack, onCheckedChange = { wholePack = it })
                        Text(stringResource(R.string.tags_whole_pack, packSize, packName), style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onSave(text, wholePack) }) { Text(stringResource(R.string.save)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

/** Picks look-alike stickers to give the tags just added to one sticker. */
@Composable
private fun LookAlikesDialog(offer: LookAlikeOffer, onToggle: (Long) -> Unit, onApply: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.look_alikes_title)) },
        text = {
            Column {
                Text(stringResource(R.string.look_alikes_body, offer.tags.joinToString(" ")))
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(80.dp),
                    contentPadding = PaddingValues(top = 12.dp),
                    modifier = Modifier.height(360.dp),
                ) {
                    items(offer.candidates, key = { it.sticker.id }) { candidate ->
                        val picked = candidate.sticker.id in offer.selected
                        Box(
                            Modifier
                                .aspectRatio(1f)
                                .padding(3.dp)
                                .border(
                                    width = if (picked) 3.dp else 1.dp,
                                    color = if (picked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                                    shape = RoundedCornerShape(8.dp),
                                )
                                .clickable { onToggle(candidate.sticker.id) },
                        ) {
                            StickerThumbnail(
                                documentUri = candidate.sticker.documentUri,
                                contentDescription = null,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(4.dp)
                                    .alpha(if (picked) 1f else 0.5f),
                            )
                            if (picked) {
                                Text(
                                    "✓",
                                    color = MaterialTheme.colorScheme.primary,
                                    style = MaterialTheme.typography.titleMedium,
                                    modifier = Modifier.align(Alignment.TopEnd).padding(end = 6.dp),
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onApply, enabled = offer.selected.isNotEmpty()) {
                Text(stringResource(R.string.look_alikes_apply, offer.selected.size))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.look_alikes_skip)) } },
    )
}
