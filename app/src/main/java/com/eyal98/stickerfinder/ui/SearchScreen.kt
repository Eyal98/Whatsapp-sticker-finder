package com.eyal98.stickerfinder.ui

import android.net.Uri
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
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
    onOpenAbout: () -> Unit,
    viewModel: SearchViewModel = viewModel(factory = SearchViewModel.Factory),
) {
    val query by viewModel.query.collectAsStateWithLifecycle()
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    // Back from a sticker's details: run the search again so edits show.
    LaunchedEffect(Unit) { viewModel.refresh() }

    Scaffold { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            // Header: logo, name, sticker count, and the other places to go.
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 12.dp),
            ) {
                AppLogo(40.dp)
                Column(Modifier.weight(1f).padding(start = 12.dp)) {
                    Text(stringResource(R.string.app_name), style = MaterialTheme.typography.titleLarge)
                    Text(
                        if (state.pending > 0) stringResource(R.string.status_indexing, state.pending, state.total)
                        else stringResource(R.string.status_count, state.total),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(onClick = onOpenAbout) {
                    Icon(Icons.Outlined.Info, contentDescription = stringResource(R.string.about_open))
                }
                IconButton(onClick = onOpenKeyboard) {
                    Icon(painterResource(R.drawable.ic_keyboard), contentDescription = stringResource(R.string.keyboard_open))
                }
                IconButton(onClick = onOpenSmartSearch) {
                    Icon(
                        painterResource(R.drawable.ic_sparkle),
                        contentDescription = stringResource(R.string.smart_search),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
            }
            if (state.pending > 0 && state.total > 0) {
                LinearProgressIndicator(
                    progress = { 1f - state.pending.toFloat() / state.total },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
                )
            }
            TextField(
                value = query,
                onValueChange = viewModel::onQueryChange,
                placeholder = { Text(stringResource(R.string.search_hint)) },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        IconButton(onClick = { viewModel.onQueryChange("") }) {
                            Icon(Icons.Filled.Clear, contentDescription = stringResource(R.string.clear_search))
                        }
                    }
                },
                singleLine = true,
                shape = CircleShape,
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                ),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
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
                    onOpenDetails = { onOpenDetails(it.id) },
                )
            }
        }
    }
}

@Composable
private fun Message(text: String) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.fillMaxWidth().padding(32.dp),
    ) {
        Mascot(140.dp)
        Text(
            text,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun StickerGrid(
    stickers: List<StickerEntity>,
    onSend: (StickerEntity) -> Unit,
    onToggleStar: (StickerEntity) -> Unit,
    onOpenDetails: (StickerEntity) -> Unit,
) {
    val starDescription = stringResource(R.string.star_description)
    LazyVerticalGrid(
        columns = GridCells.Adaptive(100.dp),
        contentPadding = PaddingValues(start = 12.dp, end = 12.dp, bottom = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        items(stickers, key = { it.id }) { sticker ->
            Surface(
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                modifier = Modifier.aspectRatio(1f),
            ) {
                Box(
                    Modifier.combinedClickable(
                        onClick = { onSend(sticker) },
                        onLongClick = { onOpenDetails(sticker) },
                    ),
                ) {
                    StickerThumbnail(
                        documentUri = sticker.documentUri,
                        contentDescription = sticker.userTags.ifBlank { sticker.userDescription ?: sticker.ocrText },
                        modifier = Modifier.fillMaxSize().padding(8.dp),
                    )
                    // Favorite: a filled amber star when on, a faint one to tap when off.
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .size(36.dp)
                            .semantics { contentDescription = starDescription }
                            .combinedClickable(onClick = { onToggleStar(sticker) }),
                    ) {
                        Surface(
                            shape = RoundedCornerShape(50),
                            color = if (sticker.starred) MaterialTheme.colorScheme.tertiaryContainer else Color.Transparent,
                        ) {
                            Icon(
                                Icons.Filled.Star,
                                contentDescription = null,
                                tint = if (sticker.starred) MaterialTheme.colorScheme.tertiary
                                else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.25f),
                                modifier = Modifier.padding(3.dp).size(16.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}
