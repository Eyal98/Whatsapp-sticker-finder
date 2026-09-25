package com.eyal98.stickerfinder.ui

import android.content.ActivityNotFoundException
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
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.eyal98.stickerfinder.R
import com.eyal98.stickerfinder.caption.ModelCatalog
import com.eyal98.stickerfinder.caption.PendingModel

@Composable
fun SmartSearchScreen(
    onBack: () -> Unit,
    viewModel: SmartSearchViewModel = viewModel(factory = SmartSearchViewModel.Factory),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val pickModel = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) viewModel.import(uri)
    }
    val recommended = ModelCatalog.RECOMMENDED
    val model = state.model

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
            Text(stringResource(R.string.smart_search_body), style = MaterialTheme.typography.bodyLarge)

            if (!state.enoughMemory) {
                Text(
                    stringResource(R.string.not_enough_memory),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyLarge,
                )
            }

            val installed = model.installed
            when {
                installed != null -> {
                    Text(stringResource(R.string.model_installed, installed.displayName), style = MaterialTheme.typography.titleMedium)
                    Text(
                        if (state.captionPending > 0) {
                            stringResource(R.string.captions_pending, state.captionPending, state.total)
                        } else {
                            stringResource(R.string.captions_done)
                        },
                    )
                    Button(onClick = viewModel::startNow, enabled = state.enoughMemory && state.captionPending > 0) {
                        Text(stringResource(R.string.start_now))
                    }
                    OutlinedButton(onClick = viewModel::removeModel) { Text(stringResource(R.string.remove_model)) }
                }
                model.importProgress != null -> {
                    Text(stringResource(R.string.importing, (model.importProgress * 100).toInt()))
                    LinearProgressIndicator(progress = { model.importProgress }, modifier = Modifier.fillMaxWidth())
                }
                else -> {
                    Text(stringResource(R.string.model_recommended, recommended.displayName, recommended.approxSize))
                    OutlinedButton(onClick = {
                        try {
                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(recommended.downloadPage)))
                        } catch (e: ActivityNotFoundException) {
                            // No browser installed; the address is shown below.
                        }
                    }) { Text(stringResource(R.string.open_download_page)) }
                    Text(recommended.downloadPage, style = MaterialTheme.typography.bodySmall)
                    Button(onClick = { pickModel.launch(arrayOf("*/*")) }) { Text(stringResource(R.string.import_model)) }
                }
            }

            when (val problem = model.problem) {
                is ImportProblem.NotEnoughSpace -> Text(
                    stringResource(R.string.import_no_space, Formatter.formatShortFileSize(context, problem.neededBytes)),
                    color = MaterialTheme.colorScheme.error,
                )
                ImportProblem.Failed -> Text(stringResource(R.string.import_failed), color = MaterialTheme.colorScheme.error)
                null -> Unit
            }
        }
    }

    model.pending?.let { pending ->
        ConfirmModelDialog(pending, onConfirm = viewModel::confirmPending, onDismiss = viewModel::discardPending)
    }
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
