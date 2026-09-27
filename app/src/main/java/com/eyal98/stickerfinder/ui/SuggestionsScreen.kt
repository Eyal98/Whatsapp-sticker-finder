package com.eyal98.stickerfinder.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.eyal98.stickerfinder.R

/**
 * Tag suggestions to review: for each of the user's tags, the stickers that look like the ones
 * that have it. Untick the wrong ones and add the tag to the rest in one tap.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SuggestionsScreen(onBack: () -> Unit, viewModel: SuggestionsViewModel = viewModel(factory = SuggestionsViewModel.Factory)) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    BackHandler(onBack = onBack)
    Scaffold { padding ->
        LazyVerticalGrid(
            columns = GridCells.Adaptive(88.dp),
            contentPadding = PaddingValues(16.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.fillMaxSize().padding(padding),
        ) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    ScreenHeader(stringResource(R.string.suggestions_title), onBack)
                    Text(stringResource(R.string.suggestions_intro), style = MaterialTheme.typography.bodyMedium)
                    if (!state.loading && state.groups.isEmpty()) {
                        Text(stringResource(R.string.suggestions_none), style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
            for (group in state.groups) {
                val key = group.tag.lowercase()
                val off = state.unticked[key].orEmpty()
                val busy = state.saving != null
                item(key = "h:$key", span = { GridItemSpan(maxLineSpan) }) {
                    Column(Modifier.padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        HorizontalDivider()
                        Text(group.tag, style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(top = 8.dp))
                        Text(
                            stringResource(R.string.suggestions_group_hint, group.stickers.size),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                items(group.stickers, key = { "s:$key:${it.id}" }) { s ->
                    val picked = s.id !in off
                    Box(
                        Modifier
                            .aspectRatio(1f)
                            .border(
                                width = if (picked) 3.dp else 1.dp,
                                color = if (picked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                                shape = RoundedCornerShape(8.dp),
                            )
                            .clickable { viewModel.toggle(group.tag, s.id) },
                    ) {
                        StickerThumbnail(
                            s.documentUri,
                            null,
                            Modifier.fillMaxSize().padding(4.dp).alpha(if (picked) 1f else 0.4f),
                        )
                        if (picked) {
                            Text("✓", color = MaterialTheme.colorScheme.primary, modifier = Modifier.align(Alignment.TopEnd).padding(end = 6.dp))
                        }
                    }
                }
                item(key = "a:$key", span = { GridItemSpan(maxLineSpan) }) {
                    val count = group.stickers.count { it.id !in off }
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { viewModel.accept(group) }, enabled = !busy && count > 0) {
                            Text(stringResource(R.string.suggestions_add, count))
                        }
                        OutlinedButton(onClick = { viewModel.reject(group) }, enabled = !busy) {
                            Text(stringResource(R.string.suggestions_none_fit))
                        }
                        TextButton(onClick = { viewModel.skip(group) }, enabled = !busy) {
                            Text(stringResource(R.string.suggestions_later))
                        }
                    }
                }
            }
        }
    }
}
