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
import androidx.compose.runtime.LaunchedEffect
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
import com.eyal98.stickerfinder.ChatImports
import com.eyal98.stickerfinder.Diagnostics
import com.eyal98.stickerfinder.R
import com.eyal98.stickerfinder.StickerFinderApp
import com.eyal98.stickerfinder.index.ChatImporter
import com.eyal98.stickerfinder.index.ImageTagStatus
import com.eyal98.stickerfinder.ml.ModelCrashGuard
import com.eyal98.stickerfinder.ml.PendingModel

@Composable
fun SmartSearchScreen(
    onBack: () -> Unit,
    onOpenQualityTest: () -> Unit,
    onOpenPeople: () -> Unit,
    onOpenSuggestions: () -> Unit,
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
    val chatLearning by viewModel.chatLearning.collectAsStateWithLifecycle()
    val pickChat = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) viewModel.importChat(uri)
    }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var diagnostics by remember { mutableStateOf<String?>(null) }
    val onImport: (ModelSlot) -> Unit = { slot ->
        pickingFor = slot
        pickFile.launch(arrayOf("*/*"))
    }

    BackHandler(onBack = onBack)
    LaunchedEffect(Unit) { viewModel.refresh() }
    Scaffold { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            ScreenHeader(stringResource(R.string.smart_search), onBack)
            Text(stringResource(R.string.smart_search_intro), style = MaterialTheme.typography.bodyLarge)

            // Picture tags: the model is part of the app, so there's nothing to install.
            if (state.pictureTagsAvailable) {
                Text(stringResource(R.string.image_tags_title), style = MaterialTheme.typography.titleLarge)
                Text(stringResource(R.string.image_tags_body), style = MaterialTheme.typography.bodyMedium)
                TurnedOffNotice(ModelCrashGuard.IMAGE_TAGS, state, viewModel::turnOn)
                val tags = state.imageTags
                if (tags.phase == ImageTagStatus.Phase.DONE) {
                    Text(stringResource(R.string.image_tags_done))
                } else {
                    Text(stringResource(R.string.image_tags_pending, tags.left, tags.total))
                    if (tags.total > 0) {
                        LinearProgressIndicator(progress = { tags.done.toFloat() / tags.total }, modifier = Modifier.fillMaxWidth())
                    }
                }
                when (tags.phase) {
                    ImageTagStatus.Phase.DONE, ImageTagStatus.Phase.TURNED_OFF -> Unit
                    ImageTagStatus.Phase.RUNNING -> Text(
                        tags.etaMillis?.let { stringResource(R.string.image_tags_running_eta, durationText(it)) }
                            ?: stringResource(R.string.image_tags_running),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    else -> {
                        Text(
                            stringResource(
                                when (tags.phase) {
                                    ImageTagStatus.Phase.BATTERY_LOW -> R.string.image_tags_battery_low
                                    ImageTagStatus.Phase.WAITING_TO_START -> R.string.image_tags_waiting_to_start
                                    else -> R.string.image_tags_waiting_for_charger
                                },
                            ),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        // Asks for notifications first (Android 13+), so the run shows its progress;
                        // tagging starts whatever the answer.
                        val askNotifications = rememberLauncherForActivityResult(
                            ActivityResultContracts.RequestPermission(),
                        ) { viewModel.startImageTags() }
                        Button(onClick = {
                            if (Build.VERSION.SDK_INT >= 33 &&
                                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
                                PackageManager.PERMISSION_GRANTED
                            ) {
                                askNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
                            } else {
                                viewModel.startImageTags()
                            }
                        }) { Text(stringResource(R.string.image_tags_start)) }
                    }
                }

                HorizontalDivider()
            }

            // Tag suggestions: the user's own tags on look-alike stickers, to approve in groups.
            Text(stringResource(R.string.suggestions_title), style = MaterialTheme.typography.titleLarge)
            Text(stringResource(R.string.suggestions_section_body), style = MaterialTheme.typography.bodyMedium)
            OutlinedButton(onClick = onOpenSuggestions) { Text(stringResource(R.string.suggestions_open)) }
            HorizontalDivider()

            // People: faces grouped and named by the user.
            Text(stringResource(R.string.people_title), style = MaterialTheme.typography.titleLarge)
            Text(stringResource(R.string.people_section_body), style = MaterialTheme.typography.bodyMedium)
            OutlinedButton(onClick = onOpenPeople) { Text(stringResource(R.string.people_open)) }
            HorizontalDivider()

            // Learn from your chats: what stickers are used for, from exported WhatsApp chats.
            ChatLearningSection(
                chatLearning,
                onImport = { pickChat.launch(CHAT_EXPORT_TYPES) },
                onForget = viewModel::forgetChats,
            )
            HorizontalDivider()

            HorizontalDivider()

            // Meaning search
            Text(stringResource(R.string.meaning_title), style = MaterialTheme.typography.titleLarge)
            Text(stringResource(R.string.meaning_body), style = MaterialTheme.typography.bodyMedium)
            if (!state.embeddingMemoryOk) ErrorText(stringResource(R.string.not_enough_memory))
            TurnedOffNotice(ModelCrashGuard.EMBEDDING, state, viewModel::turnOn)
            SlotSection(ModelSlot.EMBEDDING, state, onImport, viewModel::remove)
            if (state.slot(ModelSlot.EMBEDDING).installed != null) Text(stringResource(R.string.meaning_status, state.vectorCount, state.total))

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

/** What a WhatsApp chat export (.zip) may be labeled as by the app that saved it. */
private val CHAT_EXPORT_TYPES = arrayOf("application/zip", "application/x-zip-compressed", "application/octet-stream")

/**
 * "Learn from your chats": how to export a chat, what's kept, the import with its progress and
 * result, and what was learned so far with a way to forget it.
 */
@Composable
private fun ChatLearningSection(state: ChatLearningUiState, onImport: () -> Unit, onForget: () -> Unit) {
    Text(stringResource(R.string.chats_title), style = MaterialTheme.typography.titleLarge)
    Text(stringResource(R.string.chats_body), style = MaterialTheme.typography.bodyMedium)
    Text(stringResource(R.string.chats_privacy), style = MaterialTheme.typography.bodySmall)
    val importState = state.state
    val running = importState is ChatImports.State.Running
    when {
        importState is ChatImports.State.Running -> ChatImportProgress(importState.progress)
        !state.available -> ErrorText(stringResource(R.string.chats_no_model))
        else -> Button(onClick = onImport) { Text(stringResource(R.string.chats_import)) }
    }
    when (importState) {
        is ChatImports.State.Done -> when (val outcome = importState.outcome) {
            is ChatImporter.Outcome.Learned -> {
                val s = outcome.summary
                Text(stringResource(R.string.chats_result, s.sent, s.matched, s.learned, s.notInLibrary))
                if (s.withoutContext > 0) {
                    Text(stringResource(R.string.chats_result_no_context, s.withoutContext), style = MaterialTheme.typography.bodySmall)
                }
                when {
                    s.sent == 0 -> ErrorText(stringResource(R.string.chats_no_sends))
                    s.matched == 0 -> Text(stringResource(R.string.chats_none_matched), style = MaterialTheme.typography.bodyMedium)
                }
            }
            ChatImporter.Outcome.AlreadyImported -> Text(stringResource(R.string.chats_already))
            ChatImporter.Outcome.NotAChat -> ErrorText(stringResource(R.string.chats_not_a_chat))
            ChatImporter.Outcome.NoMedia -> ErrorText(stringResource(R.string.chats_no_media))
            ChatImporter.Outcome.NoModel -> ErrorText(stringResource(R.string.chats_no_model))
        }
        ChatImports.State.Failed -> ErrorText(stringResource(R.string.chats_failed))
        ChatImports.State.Idle, is ChatImports.State.Running -> Unit
    }
    if (state.chats > 0 || state.stickers > 0) {
        Text(stringResource(R.string.chats_totals, state.chats, state.stickers), style = MaterialTheme.typography.bodyMedium)
        OutlinedButton(onClick = onForget, enabled = !running) { Text(stringResource(R.string.chats_forget)) }
    }
}

@Composable
private fun ChatImportProgress(progress: ChatImporter.Progress?) {
    when {
        progress != null && progress.step == ChatImporter.Step.LEARNING -> {
            Text(stringResource(R.string.chats_learning, progress.done.toInt(), progress.total.toInt()))
            LinearProgressIndicator(
                progress = { if (progress.total > 0) progress.done.toFloat() / progress.total else 0f },
                modifier = Modifier.fillMaxWidth(),
            )
        }
        progress != null && progress.total > 0 -> {
            val fraction = (progress.done.toFloat() / progress.total).coerceIn(0f, 1f)
            Text(stringResource(R.string.chats_reading_percent, (fraction * 100).toInt()))
            LinearProgressIndicator(progress = { fraction }, modifier = Modifier.fillMaxWidth())
        }
        else -> {
            Text(stringResource(R.string.chats_reading))
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }
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
        installed != null && s.builtIn -> {
            Text(stringResource(R.string.model_built_in, installed.displayName), style = MaterialTheme.typography.titleMedium)
        }
        installed != null -> {
            Text(stringResource(R.string.model_installed, installed.displayName), style = MaterialTheme.typography.titleMedium)
            OutlinedButton(onClick = { onRemove(slot) }) { Text(stringResource(R.string.remove_model)) }
        }
        s.bundled -> {
            // The app copies its bundled model into place on start; it's missing only while that
            // runs or when the phone is out of space.
            Text(stringResource(R.string.model_setting_up))
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
    val reason = ModelCrashGuard.reason(LocalContext.current, feature)
    if (reason != null) {
        Text(stringResource(R.string.model_turned_off_reason, reason), style = MaterialTheme.typography.bodySmall)
    }
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
fun DiagnosticsDialog(report: String, onDismiss: () -> Unit) {
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
                            .setPrimaryClip(ClipData.newPlainText("Peel-It diagnostics", report))
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

