package com.eyal98.stickerfinder.ui

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.text.format.Formatter
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch
import com.eyal98.stickerfinder.Diagnostics
import com.eyal98.stickerfinder.R
import com.eyal98.stickerfinder.StickerFinderApp
import com.eyal98.stickerfinder.index.CaptionStatus
import com.eyal98.stickerfinder.ml.ModelCatalog
import com.eyal98.stickerfinder.ml.ModelCrashGuard
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
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var diagnostics by remember { mutableStateOf<String?>(null) }
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

            // Picture tags
            if (state.pictureTagsAvailable) {
                Text(stringResource(R.string.image_tags_title), style = MaterialTheme.typography.titleLarge)
                Text(stringResource(R.string.image_tags_body), style = MaterialTheme.typography.bodyMedium)
                TurnedOffNotice(ModelCrashGuard.IMAGE_TAGS, state, viewModel::turnOn)
                SlotSection(ModelSlot.IMAGE, state, onImport, viewModel::remove)
                state.slot(ModelSlot.IMAGE).installed?.let { installed ->
                    if (installed.sha256 != ModelCatalog.SIGLIP2_B16.sha256) {
                        ErrorText(stringResource(R.string.image_tags_wrong_file))
                    } else {
                        Text(
                            if (state.imageTagPending > 0) {
                                stringResource(R.string.image_tags_pending, state.imageTagPending, state.total)
                            } else {
                                stringResource(R.string.image_tags_done)
                            },
                        )
                        if (state.imageTagRunning) {
                            Text(
                                stringResource(R.string.image_tags_running),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        } else if (state.imageTagPending > 0) {
                            Button(onClick = viewModel::startImageTags) { Text(stringResource(R.string.image_tags_start)) }
                        }
                    }
                }

                HorizontalDivider()
            }

            // Descriptions
            Text(stringResource(R.string.captions_title), style = MaterialTheme.typography.titleLarge)
            Text(stringResource(R.string.smart_search_body), style = MaterialTheme.typography.bodyMedium)
            if (!state.captionMemoryOk) ErrorText(stringResource(R.string.not_enough_memory))
            TurnedOffNotice(ModelCrashGuard.CAPTION, state, viewModel::turnOn)
            SlotSection(ModelSlot.CAPTION, state, onImport, viewModel::remove)
            if (state.slot(ModelSlot.CAPTION).installed != null) {
                Text(
                    if (state.captionPending > 0) {
                        stringResource(R.string.captions_pending, state.captionPending, state.total)
                    } else {
                        stringResource(R.string.captions_done)
                    },
                )
                CaptionStatusText(state.captionStatus)
                // Asks for notifications first (Android 13+), so a run on the charger shows its
                // progress; captioning starts whatever the answer.
                val askNotifications = rememberLauncherForActivityResult(
                    ActivityResultContracts.RequestPermission(),
                ) { viewModel.startCaptioning() }
                Button(
                    onClick = {
                        if (Build.VERSION.SDK_INT >= 33 &&
                            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
                            PackageManager.PERMISSION_GRANTED
                        ) {
                            askNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
                        } else {
                            viewModel.startCaptioning()
                        }
                    },
                    enabled = state.captionMemoryOk && state.captionPending > 0,
                ) { Text(stringResource(R.string.start_now)) }
            }

            HorizontalDivider()

            // Meaning search
            Text(stringResource(R.string.meaning_title), style = MaterialTheme.typography.titleLarge)
            Text(stringResource(R.string.meaning_body), style = MaterialTheme.typography.bodyMedium)
            if (!state.embeddingMemoryOk) ErrorText(stringResource(R.string.not_enough_memory))
            TurnedOffNotice(ModelCrashGuard.EMBEDDING, state, viewModel::turnOn)
            SlotSection(ModelSlot.EMBEDDING, state, onImport, viewModel::remove)
            SlotSection(ModelSlot.TOKENIZER, state, onImport, viewModel::remove)
            val embeddingReady = state.slot(ModelSlot.EMBEDDING).installed != null &&
                state.slot(ModelSlot.TOKENIZER).installed != null
            if (embeddingReady) Text(stringResource(R.string.meaning_status, state.vectorCount, state.total))

            HorizontalDivider()
            Text(stringResource(R.string.eval_title), style = MaterialTheme.typography.titleLarge)
            Text(stringResource(R.string.eval_entry_body), style = MaterialTheme.typography.bodyMedium)
            OutlinedButton(onClick = onOpenQualityTest) { Text(stringResource(R.string.eval_open)) }

            HorizontalDivider()
            Text(stringResource(R.string.diag_title), style = MaterialTheme.typography.titleLarge)
            Text(stringResource(R.string.diag_body), style = MaterialTheme.typography.bodyMedium)
            OutlinedButton(
                onClick = {
                    scope.launch {
                        diagnostics = Diagnostics.build(context.applicationContext as StickerFinderApp)
                    }
                },
            ) { Text(stringResource(R.string.diag_button)) }
        }
    }

    diagnostics?.let { report ->
        DiagnosticsDialog(report, onDismiss = { diagnostics = null })
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
        is ImportProblem.WrongFileType -> ErrorText(
            stringResource(R.string.import_wrong_type, problem.expected.joinToString(" / ") { ".$it" }),
        )
        ImportProblem.Failed -> ErrorText(stringResource(R.string.import_failed))
        null -> Unit
    }
}

/** Shown when a feature was turned off because its model crashed the app. */
@Composable
private fun TurnedOffNotice(feature: String, state: SmartSearchUiState, onTurnOn: (String) -> Unit) {
    if (feature !in state.turnedOff) return
    ErrorText(stringResource(R.string.model_turned_off))
    OutlinedButton(onClick = { onTurnOn(feature) }) { Text(stringResource(R.string.model_turn_on)) }
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

/** Shows the full report first, so the user sees exactly what they'd share. */
@Composable
private fun DiagnosticsDialog(report: String, onDismiss: () -> Unit) {
    val context = LocalContext.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.diag_preview_title)) },
        text = {
            Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
                Text(report, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, report)
                    context.startActivity(Intent.createChooser(send, null))
                },
            ) { Text(stringResource(R.string.diag_share)) }
        },
        dismissButton = {
            Row {
                TextButton(
                    onClick = {
                        context.getSystemService(ClipboardManager::class.java)
                            .setPrimaryClip(ClipData.newPlainText("Sticker Finder diagnostics", report))
                    },
                ) { Text(stringResource(R.string.diag_copy)) }
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.diag_close)) }
            }
        },
    )
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

/** Whether the caption model is working right now, so the user can tell it's running. */
@Composable
private fun CaptionStatusText(status: CaptionStatus) {
    val text = when (status) {
        CaptionStatus.Idle -> return
        CaptionStatus.Waiting -> stringResource(R.string.caption_status_waiting)
        CaptionStatus.Loading -> stringResource(R.string.caption_status_loading)
        is CaptionStatus.Describing -> stringResource(R.string.caption_status_running, status.done)
    }
    Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
}
