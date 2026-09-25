package com.eyal98.stickerfinder.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.text.format.Formatter
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.eyal98.stickerfinder.R
import com.eyal98.stickerfinder.ml.PendingModel

@Composable
fun SmartSearchScreen(
    onBack: () -> Unit,
    onOpenQualityTest: () -> Unit,
    viewModel: SmartSearchViewModel = viewModel(factory = SmartSearchViewModel.Factory),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    // Which slot the file picker was opened for.
    var pickingFor by rememberSaveable { mutableStateOf<ModelSlot?>(null) }
    val pickFile = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        val slot = pickingFor
        if (uri != null && slot != null) viewModel.import(slot, uri)
        pickingFor = null
    }
    val onImport: (ModelSlot) -> Unit = { slot ->
        pickingFor = slot
        pickFile.launch(arrayOf("*/*"))
    }

    BackHandler(onBack = onBack)
    Scaffold { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            TextButton(onClick = onBack) { Text(stringResource(R.string.back)) }
            Text(stringResource(R.string.smart_search), style = MaterialTheme.typography.headlineSmall)
            Text(stringResource(R.string.smart_search_intro), style = MaterialTheme.typography.bodyLarge)

            // Descriptions
            Text(stringResource(R.string.captions_title), style = MaterialTheme.typography.titleLarge)
            Text(stringResource(R.string.smart_search_body), style = MaterialTheme.typography.bodyMedium)
            if (!state.captionMemoryOk) ErrorText(stringResource(R.string.not_enough_memory))
            SlotSection(ModelSlot.CAPTION, state, onImport, viewModel::remove)
            if (state.slot(ModelSlot.CAPTION).installed != null) {
                Text(
                    if (state.captionPending > 0) {
                        stringResource(R.string.captions_pending, state.captionPending, state.total)
                    } else {
                        stringResource(R.string.captions_done)
                    },
                )
                Button(
                    onClick = viewModel::startCaptioning,
                    enabled = state.captionMemoryOk && state.captionPending > 0,
                ) { Text(stringResource(R.string.start_now)) }
            }

            HorizontalDivider()

            // Meaning search
            Text(stringResource(R.string.meaning_title), style = MaterialTheme.typography.titleLarge)
            Text(stringResource(R.string.meaning_body), style = MaterialTheme.typography.bodyMedium)
            if (!state.embeddingMemoryOk) ErrorText(stringResource(R.string.not_enough_memory))
            SlotSection(ModelSlot.EMBEDDING, state, onImport, viewModel::remove)
            SlotSection(ModelSlot.TOKENIZER, state, onImport, viewModel::remove)
            val embeddingReady = state.slot(ModelSlot.EMBEDDING).installed != null &&
                state.slot(ModelSlot.TOKENIZER).installed != null
            if (embeddingReady) Text(stringResource(R.string.meaning_status, state.vectorCount, state.total))

            HorizontalDivider()
            Text(stringResource(R.string.eval_title), style = MaterialTheme.typography.titleLarge)
            Text(stringResource(R.string.eval_entry_body), style = MaterialTheme.typography.bodyMedium)
            OutlinedButton(onClick = onOpenQualityTest) { Text(stringResource(R.string.eval_open)) }
        }
    }

    state.firstPending?.let { (slot, pending) ->
        ConfirmModelDialog(
            pending,
            onConfirm = { viewModel.confirmPending(slot) },
            onDismiss = { viewModel.discardPending(slot) },
        )
    }
}

/** One model file: installed (with Remove), being imported, or not installed (with Import). */
@Composable
private fun SlotSection(
    slot: ModelSlot,
    state: SmartSearchUiState,
    onImport: (ModelSlot) -> Unit,
    onRemove: (ModelSlot) -> Unit,
) {
    val context = LocalContext.current
    val s = state.slot(slot)
    val installed = s.installed
    val progress = s.importProgress
    when {
        installed != null -> {
            Text(stringResource(R.string.model_installed, installed.displayName), style = MaterialTheme.typography.titleMedium)
            OutlinedButton(onClick = { onRemove(slot) }) { Text(stringResource(R.string.remove_model)) }
        }
        progress != null -> {
            Text(stringResource(R.string.importing, (progress * 100).toInt()))
            LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
        }
        else -> {
            val spec = slot.recommended
            Text(stringResource(R.string.model_file_needed, spec.fileName, spec.approxSize))
            OutlinedButton(onClick = { openPage(context, spec.downloadPage) }) {
                Text(stringResource(R.string.open_download_page))
            }
            Text(spec.downloadPage, style = MaterialTheme.typography.bodySmall)
            Button(onClick = { onImport(slot) }, enabled = !state.importing) {
                Text(stringResource(R.string.import_model))
            }
        }
    }
    when (val problem = s.problem) {
        is ImportProblem.NotEnoughSpace -> ErrorText(
            stringResource(R.string.import_no_space, Formatter.formatShortFileSize(context, problem.neededBytes)),
        )
        ImportProblem.Failed -> ErrorText(stringResource(R.string.import_failed))
        null -> Unit
    }
}

private fun openPage(context: Context, url: String) {
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    } catch (e: ActivityNotFoundException) {
        // No browser installed; the address is shown on screen.
    }
}

@Composable
private fun ErrorText(text: String) {
    Text(text, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyLarge)
}

@Composable
private fun ConfirmModelDialog(pending: PendingModel, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.confirm_model_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.confirm_model_body, pending.guessedModel?.displayName ?: pending.originalName))
                Text(pending.sha256, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = { TextButton(onClick = onConfirm) { Text(stringResource(R.string.use_model)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}
