package com.eyal98.stickerfinder.ui

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.eyal98.stickerfinder.R
import com.eyal98.stickerfinder.StickerFinderApp
import com.eyal98.stickerfinder.index.backup.Backup
import com.eyal98.stickerfinder.index.backup.BackupFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Backing up what the user made (tags, descriptions, stars, search history...) to a file they
 * choose, and restoring it, e.g. on a new phone. The app has no internet: the file goes wherever
 * the user saves it. See [Backup] for what's in it.
 */
@Composable
fun BackupSection() {
    val context = LocalContext.current
    val app = context.applicationContext as StickerFinderApp
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }

    // Backing up: options first, then where to save.
    var askOptions by remember { mutableStateOf(false) }
    var password by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    val save = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        busy = true
        scope.launch {
            message = try {
                val json = Backup.create(app, app.database.stickerDao())
                withContext(Dispatchers.Default) { BackupFile.write(json, password.toCharArray()) }.let { write(context, uri, it) }
                context.getString(R.string.backup_saved)
            } catch (e: IOException) {
                context.getString(R.string.backup_failed)
            } finally {
                password = ""
                confirm = ""
                busy = false
            }
        }
    }

    // Restoring: pick the file, then its password if it has one.
    var restoreBytes by remember { mutableStateOf<ByteArray?>(null) }
    var restorePassword by remember { mutableStateOf("") }
    fun restore(bytes: ByteArray, pass: String?) {
        busy = true
        scope.launch {
            message = try {
                val json = withContext(Dispatchers.Default) { BackupFile.read(bytes, pass?.toCharArray()) }
                val report = Backup.restore(app, app.database.stickerDao(), app.repository, json)
                restoreBytes = null
                describe(context, report)
            } catch (e: BackupFile.NeedsPassword) {
                restoreBytes = bytes
                null
            } catch (e: BackupFile.WrongPassword) {
                context.getString(R.string.restore_wrong_password)
            } catch (e: IOException) {
                restoreBytes = null
                context.getString(R.string.restore_not_backup)
            } finally {
                restorePassword = ""
                busy = false
            }
        }
    }
    val open = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val bytes = withContext(Dispatchers.IO) {
                try {
                    context.contentResolver.openInputStream(uri)?.use(BackupFile::readFile)
                } catch (e: IOException) {
                    null
                }
            }
            if (bytes == null) message = context.getString(R.string.restore_not_backup) else restore(bytes, null)
        }
    }

    Text(stringResource(R.string.backup_title), style = MaterialTheme.typography.titleLarge)
    Text(stringResource(R.string.backup_body), style = MaterialTheme.typography.bodyMedium)
    val waitingStickers = remember(message) { Backup.waiting(context) }
    if (waitingStickers > 0) {
        Text(stringResource(R.string.restore_waiting, waitingStickers), style = MaterialTheme.typography.bodySmall)
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(onClick = { askOptions = true }, enabled = !busy) { Text(stringResource(R.string.backup_button)) }
        OutlinedButton(onClick = { open.launch(arrayOf("*/*")) }, enabled = !busy) { Text(stringResource(R.string.restore_button)) }
    }
    if (busy) Text(stringResource(R.string.backup_working), style = MaterialTheme.typography.bodySmall)

    if (askOptions) {
        val mismatch = password.isNotEmpty() && password != confirm
        AlertDialog(
            onDismissRequest = { askOptions = false },
            title = { Text(stringResource(R.string.backup_button)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        label = { Text(stringResource(R.string.backup_password)) },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    )
                    if (password.isNotEmpty()) {
                        OutlinedTextField(
                            value = confirm,
                            onValueChange = { confirm = it },
                            label = { Text(stringResource(R.string.backup_password_again)) },
                            singleLine = true,
                            isError = mismatch,
                            visualTransformation = PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        )
                    }
                    Text(stringResource(R.string.backup_password_note), style = MaterialTheme.typography.bodySmall)
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        askOptions = false
                        save.launch("peel-it-backup-${SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())}.peelit")
                    },
                    enabled = !mismatch,
                ) { Text(stringResource(R.string.backup_save)) }
            },
            dismissButton = { TextButton(onClick = { askOptions = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }

    restoreBytes?.let { bytes ->
        AlertDialog(
            onDismissRequest = { restoreBytes = null },
            title = { Text(stringResource(R.string.restore_button)) },
            text = {
                OutlinedTextField(
                    value = restorePassword,
                    onValueChange = { restorePassword = it },
                    label = { Text(stringResource(R.string.backup_password)) },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                )
            },
            confirmButton = {
                TextButton(onClick = { restore(bytes, restorePassword) }, enabled = restorePassword.isNotEmpty() && !busy) {
                    Text(stringResource(R.string.restore_button))
                }
            },
            dismissButton = { TextButton(onClick = { restoreBytes = null }) { Text(stringResource(R.string.cancel)) } },
        )
    }

    message?.let { text ->
        AlertDialog(
            onDismissRequest = { message = null },
            text = { Text(text) },
            confirmButton = { TextButton(onClick = { message = null }) { Text(stringResource(R.string.ok)) } },
        )
    }
}

private suspend fun write(context: Context, uri: Uri, bytes: ByteArray) = withContext(Dispatchers.IO) {
    context.contentResolver.openOutputStream(uri, "wt")?.use { it.write(bytes) } ?: throw IOException("Could not open the file")
}

private fun describe(context: Context, r: Backup.RestoreReport): String = buildString {
    append(context.getString(R.string.restore_done, r.stickersRestored, r.picksRestored, r.testSearches))
    if (r.stickersWaiting > 0) append("\n\n").append(context.getString(R.string.restore_stickers_waiting, r.stickersWaiting))
}
