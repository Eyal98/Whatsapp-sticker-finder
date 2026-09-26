package com.eyal98.stickerfinder.ui

import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.eyal98.stickerfinder.R
import com.eyal98.stickerfinder.data.StickerEntity

/** One described sticker at a time: is its description good, too generic, or wrong? */
@Composable
fun DescriptionReviewScreen(
    onBack: () -> Unit,
    viewModel: DescriptionReviewViewModel = viewModel(factory = DescriptionReviewViewModel.Factory),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    BackHandler(onBack = onBack)
    Scaffold { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.review_title), style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                TextButton(onClick = onBack) { Text(stringResource(R.string.back)) }
            }
            val current = state.current
            when {
                state.loading -> Text(stringResource(R.string.review_loading))
                state.stickers.isEmpty() -> Text(stringResource(R.string.review_none))
                current != null -> {
                    Text(stringResource(R.string.review_intro), style = MaterialTheme.typography.bodyMedium)
                    Text(
                        stringResource(R.string.review_progress, state.index + 1, state.stickers.size),
                        style = MaterialTheme.typography.labelMedium,
                    )
                    StickerCard(current)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        Button(onClick = { viewModel.judge(Verdict.GOOD) }, modifier = Modifier.weight(1f)) {
                            Text(stringResource(R.string.review_good))
                        }
                        OutlinedButton(onClick = { viewModel.judge(Verdict.GENERIC) }, modifier = Modifier.weight(1f)) {
                            Text(stringResource(R.string.review_generic))
                        }
                        OutlinedButton(onClick = { viewModel.judge(Verdict.WRONG) }, modifier = Modifier.weight(1f)) {
                            Text(stringResource(R.string.review_wrong))
                        }
                    }
                    TextButton(onClick = viewModel::skip) { Text(stringResource(R.string.review_skip)) }
                }
                else -> {
                    val verdicts = state.verdicts.values
                    val good = verdicts.count { it == Verdict.GOOD }
                    Text(
                        stringResource(
                            R.string.review_summary,
                            verdicts.size,
                            good,
                            verdicts.count { it == Verdict.GENERIC },
                            verdicts.count { it == Verdict.WRONG },
                        ),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    if (verdicts.isNotEmpty()) {
                        Text(
                            stringResource(R.string.review_percent, good * 100 / verdicts.size),
                            style = MaterialTheme.typography.headlineSmall,
                        )
                    }
                    Text(stringResource(R.string.review_share_body), style = MaterialTheme.typography.bodyMedium)
                    Button(onClick = {
                        val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, viewModel.report())
                        context.startActivity(Intent.createChooser(send, null))
                    }) { Text(stringResource(R.string.review_share)) }
                    OutlinedButton(onClick = viewModel::restart) { Text(stringResource(R.string.review_again)) }
                }
            }
        }
    }
}

@Composable
private fun StickerCard(sticker: StickerEntity) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        StickerThumbnail(
            documentUri = sticker.documentUri,
            contentDescription = null,
            modifier = Modifier.size(200.dp).align(Alignment.CenterHorizontally),
        )
        val empty = sticker.captionEn == null && sticker.captionHe == null && sticker.captionTags == null
        if (empty) Text(stringResource(R.string.review_empty), style = MaterialTheme.typography.bodyLarge)
        sticker.captionEn?.let { Text(it, style = MaterialTheme.typography.bodyLarge) }
        sticker.captionHe?.let { Text(it, style = MaterialTheme.typography.bodyLarge) }
        sticker.captionTags?.let { Labeled(stringResource(R.string.review_keywords), it) }
        sticker.imageTags?.takeIf { it.isNotBlank() }?.let { Labeled(stringResource(R.string.review_picture_tags), it) }
        sticker.packName?.let { Labeled(stringResource(R.string.review_pack), it) }
    }
}

@Composable
private fun Labeled(label: String, value: String) {
    Column {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}
