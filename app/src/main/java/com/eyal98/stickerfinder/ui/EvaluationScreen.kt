package com.eyal98.stickerfinder.ui

import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.eyal98.stickerfinder.R
import com.eyal98.stickerfinder.data.EvaluationReport
import com.eyal98.stickerfinder.data.Ranker
import com.eyal98.stickerfinder.data.StickerEntity
import com.eyal98.stickerfinder.data.StickerRepository
import com.eyal98.stickerfinder.search.QueryLanguage
import com.eyal98.stickerfinder.search.Score
import kotlinx.coroutines.delay
import java.util.Locale

@Composable
fun EvaluationScreen(
    onBack: () -> Unit,
    viewModel: EvaluationViewModel = viewModel(factory = EvaluationViewModel.Factory),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val editing = state.editing
    BackHandler(onBack = if (editing != null) viewModel::cancelEditing else onBack)
    Scaffold { padding ->
        Box(Modifier.padding(padding)) {
            if (editing != null) {
                EditTestSearch(editing, viewModel)
            } else {
                TestOverview(state, viewModel, onBack)
            }
        }
    }
}

@Composable
private fun TestOverview(state: EvaluationUiState, viewModel: EvaluationViewModel, onBack: () -> Unit) {
    val context = LocalContext.current
    val exportFile = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) viewModel.export(uri)
    }
    val importFile = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) viewModel.import(uri)
    }
    val languages = state.queries.groupingBy { it.language }.eachCount()

    LaunchedEffect(state.message) {
        if (state.message != null) {
            delay(MESSAGE_MS)
            viewModel.clearMessage()
        }
    }

    LazyColumn(
        contentPadding = PaddingValues(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        item { ScreenHeader(stringResource(R.string.eval_title), onBack) }
        item { Text(stringResource(R.string.eval_intro), style = MaterialTheme.typography.bodyMedium) }
        item {
            Text(
                stringResource(
                    R.string.eval_counts,
                    state.queries.size,
                    languages[QueryLanguage.HEBREW] ?: 0,
                    languages[QueryLanguage.ENGLISH] ?: 0,
                    languages[QueryLanguage.MIXED] ?: 0,
                ),
                style = MaterialTheme.typography.titleSmall,
            )
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = viewModel::startAdding) { Text(stringResource(R.string.eval_add)) }
                Button(onClick = viewModel::run, enabled = state.queries.isNotEmpty() && state.progress == null) {
                    Text(stringResource(R.string.eval_run))
                }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { exportFile.launch("sticker-test-searches.json") }, enabled = state.queries.isNotEmpty()) {
                    Text(stringResource(R.string.eval_export))
                }
                OutlinedButton(onClick = { importFile.launch(arrayOf("application/json", "text/*")) }) {
                    Text(stringResource(R.string.eval_import))
                }
            }
        }
        state.message?.let { message -> item { Text(messageText(message), color = MaterialTheme.colorScheme.primary) } }
        state.progress?.let { (done, total) ->
            item {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(stringResource(R.string.eval_running, done, total))
                    LinearProgressIndicator(
                        progress = { if (total == 0) 0f else done.toFloat() / total },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
        state.report?.let { report ->
            item {
                ReportCard(
                    report,
                    onApplyThreshold = viewModel::applyThreshold,
                    onShare = {
                        val send = Intent(Intent.ACTION_SEND)
                            .setType("text/plain")
                            .putExtra(Intent.EXTRA_TEXT, reportText(report, state.queries.size))
                        context.startActivity(Intent.createChooser(send, null))
                    },
                )
            }
        }
        items(state.queries, key = { it.id }) { query ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(query.text, style = MaterialTheme.typography.bodyLarge)
                    Text(
                        stringResource(R.string.eval_query_stickers, query.relevant.size),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                TextButton(onClick = { viewModel.delete(query) }) { Text(stringResource(R.string.eval_delete)) }
            }
        }
    }
}

@Composable
private fun messageText(message: EvaluationMessage): String = stringResource(
    when (message) {
        EvaluationMessage.SAVE_FAILED -> R.string.eval_save_failed
        EvaluationMessage.EXPORTED -> R.string.eval_exported
        EvaluationMessage.EXPORT_FAILED -> R.string.eval_export_failed
        EvaluationMessage.IMPORTED -> R.string.eval_imported
        EvaluationMessage.IMPORT_FAILED -> R.string.eval_import_failed
        EvaluationMessage.THRESHOLD_APPLIED -> R.string.eval_threshold_applied
    },
)

@Composable
private fun ReportCard(report: EvaluationReport, onApplyThreshold: (Float) -> Unit, onShare: () -> Unit) {
    Surface(tonalElevation = 2.dp, shape = MaterialTheme.shapes.medium) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                stringResource(if (report.targetsMet) R.string.eval_targets_met else R.string.eval_targets_missed),
                style = MaterialTheme.typography.titleMedium,
                color = if (report.targetsMet) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
            )
            Text(stringResource(R.string.eval_metrics_legend), style = MaterialTheme.typography.bodySmall)
            for (ranker in Ranker.entries) {
                if (ranker != Ranker.KEYWORD && !report.semanticAvailable) continue
                val scores = report.scores[ranker].orEmpty()
                Text(rankerName(ranker), style = MaterialTheme.typography.labelLarge)
                Text(scoreLine(scores), fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall)
            }
            if (!report.semanticAvailable) Text(stringResource(R.string.eval_no_meaning))
            Text(stringResource(R.string.eval_latency, report.latencyP50Ms, report.latencyP95Ms))
            val best = report.bestThreshold
            if (best != null) {
                Text(stringResource(R.string.eval_threshold, fmt(report.threshold), fmt(best)))
                if (best != report.threshold) {
                    OutlinedButton(onClick = { onApplyThreshold(best) }) {
                        Text(stringResource(R.string.eval_use_threshold, fmt(best)))
                    }
                }
            }
            if (report.skipped > 0) Text(stringResource(R.string.eval_skipped, report.skipped))
            if (report.misses.isNotEmpty()) {
                Text(stringResource(R.string.eval_misses), style = MaterialTheme.typography.labelLarge)
                report.misses.take(MAX_MISSES).forEach { Text("• $it", style = MaterialTheme.typography.bodySmall) }
            }
            TextButton(onClick = onShare) { Text(stringResource(R.string.eval_share)) }
        }
    }
}

@Composable
private fun rankerName(ranker: Ranker): String = stringResource(
    when (ranker) {
        Ranker.KEYWORD -> R.string.eval_ranker_keyword
        Ranker.MEANING -> R.string.eval_ranker_meaning
        Ranker.COMBINED -> R.string.eval_ranker_combined
    },
)

/** "all 0.82/0.75 · he 0.80/0.71 · en 0.85/0.79" (Recall@5 / MRR@10). */
private fun scoreLine(scores: Map<QueryLanguage?, Score>): String =
    listOf(null to "all", QueryLanguage.HEBREW to "he", QueryLanguage.ENGLISH to "en", QueryLanguage.MIXED to "mix")
        .mapNotNull { (lang, label) -> scores[lang]?.let { "$label ${fmt(it.recallAt5)}/${fmt(it.mrrAt10)}" } }
        .joinToString(" · ")

private fun fmt(value: Double) = String.format(Locale.US, "%.2f", value)
private fun fmt(value: Float) = String.format(Locale.US, "%.2f", value)

/** Plain-text summary for sharing: metrics and the text of missed test searches, no images. */
private fun reportText(report: EvaluationReport, queryCount: Int): String = buildString {
    appendLine("Peel-It search quality test ($queryCount test searches, ${report.skipped} skipped)")
    appendLine("Targets met: ${report.targetsMet}  (Recall@5 >= 0.80, Hebrew within 0.05 of English)")
    appendLine("Recall@5/MRR@10:")
    for (ranker in Ranker.entries) appendLine("  ${ranker.name.lowercase()}: ${scoreLine(report.scores[ranker].orEmpty())}")
    appendLine("Latency: p50 ${report.latencyP50Ms} ms, p95 ${report.latencyP95Ms} ms")
    appendLine("Similarity cut-off: current ${fmt(report.threshold)}, best ${report.bestThreshold?.let { fmt(it) } ?: "n/a"}")
    appendLine("Sweep: " + report.sweep.joinToString(" ") { (t, s) -> "${fmt(t)}=${fmt(s.recallAt5)}" })
    if (report.misses.isNotEmpty()) {
        appendLine("Missed (top 5 incomplete):")
        report.misses.forEach { appendLine("  - $it") }
    }
}

@Composable
private fun EditTestSearch(edit: EditState, viewModel: EvaluationViewModel) {
    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value = edit.text,
            onValueChange = viewModel::setText,
            label = { Text(stringResource(R.string.eval_query_label)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Text(stringResource(R.string.eval_pick_help), style = MaterialTheme.typography.bodySmall)
        OutlinedTextField(
            value = edit.finder,
            onValueChange = viewModel::setFinder,
            label = { Text(stringResource(R.string.eval_finder_label)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        if (edit.selected.isNotEmpty()) {
            Text(stringResource(R.string.eval_selected, edit.selected.size), style = MaterialTheme.typography.labelLarge)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                items(edit.selected.values.toList(), key = { StickerRepository.imageKey(it) }) { sticker ->
                    StickerThumbnail(
                        sticker.documentUri,
                        contentDescription = null,
                        modifier = Modifier.size(56.dp).clickable { viewModel.toggle(sticker) },
                    )
                }
            }
        }
        LazyVerticalGrid(
            columns = GridCells.Adaptive(88.dp),
            modifier = Modifier.weight(1f),
        ) {
            items(edit.candidates, key = { it.id }) { sticker ->
                PickableSticker(sticker, StickerRepository.imageKey(sticker) in edit.selected) { viewModel.toggle(sticker) }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = viewModel::saveEditing, enabled = edit.canSave) { Text(stringResource(R.string.save)) }
            OutlinedButton(onClick = viewModel::cancelEditing) { Text(stringResource(R.string.cancel)) }
        }
    }
}

@Composable
private fun PickableSticker(sticker: StickerEntity, selected: Boolean, onToggle: () -> Unit) {
    Surface(
        shape = MaterialTheme.shapes.small,
        border = if (selected) BorderStroke(3.dp, MaterialTheme.colorScheme.primary) else null,
        modifier = Modifier.padding(4.dp).aspectRatio(1f).clickable(onClick = onToggle),
    ) {
        Box {
            StickerThumbnail(sticker.documentUri, contentDescription = null, modifier = Modifier.fillMaxSize())
            if (selected) {
                Text(
                    "✓",
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.align(Alignment.TopEnd).padding(4.dp),
                )
            }
        }
    }
}

private const val MESSAGE_MS = 3_000L
private const val MAX_MISSES = 20
