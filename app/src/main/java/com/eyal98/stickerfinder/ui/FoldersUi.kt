package com.eyal98.stickerfinder.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.eyal98.stickerfinder.R
import com.eyal98.stickerfinder.data.FolderSummary

/**
 * The row of folder chips: "All", then the user's folders; tap to browse one. On the main screen
 * a long press offers rename and delete, and a last chip makes a new folder; the keyboard shows
 * the plain row.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FolderChips(
    folders: List<FolderSummary>,
    selected: Long?,
    onSelect: (Long?) -> Unit,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
    onLongPress: ((FolderSummary) -> Unit)? = null,
    onNew: (() -> Unit)? = null,
) {
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        contentPadding = PaddingValues(horizontal = if (compact) 4.dp else 16.dp),
        modifier = modifier,
    ) {
        item(key = "all") { Chip(stringResource(R.string.folders_all), selected == null, compact, onClick = { onSelect(null) }) }
        items(folders, key = { it.id }) { f ->
            Chip(
                "📁 ${f.name} · ${f.count}",
                selected == f.id,
                compact,
                onClick = { onSelect(if (selected == f.id) null else f.id) },
                onLongClick = onLongPress?.let { press -> { press(f) } },
            )
        }
        if (onNew != null) item(key = "new") { Chip(stringResource(R.string.folders_new_chip), false, compact, onClick = onNew) }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun Chip(text: String, selected: Boolean, compact: Boolean, onClick: () -> Unit, onLongClick: (() -> Unit)? = null) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier.combinedClickable(onClick = onClick, onLongClick = onLongClick),
    ) {
        Text(
            text,
            style = if (compact) MaterialTheme.typography.labelMedium else MaterialTheme.typography.labelLarge,
            color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = if (compact) 4.dp else 8.dp),
        )
    }
}

/** Asks for a folder's name: a new folder, or renaming [initial]. */
@Composable
fun FolderNameDialog(initial: String = "", onDone: (String) -> Unit, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(if (initial.isEmpty()) R.string.folders_new_title else R.string.folders_rename)) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it.take(MAX_NAME) },
                placeholder = { Text(stringResource(R.string.folders_name_hint)) },
                singleLine = true,
            )
        },
        confirmButton = {
            TextButton(onClick = { onDone(name.trim()) }, enabled = name.isNotBlank()) { Text(stringResource(R.string.save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

/** A folder's options (long press on its chip): rename or delete. */
@Composable
fun FolderOptionsDialog(folder: FolderSummary, onRename: (String) -> Unit, onDelete: () -> Unit, onDismiss: () -> Unit) {
    var renaming by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    when {
        renaming -> FolderNameDialog(folder.name, onDone = { onRename(it); onDismiss() }, onDismiss = onDismiss)
        confirmDelete -> AlertDialog(
            onDismissRequest = onDismiss,
            text = { Text(stringResource(R.string.folders_delete_confirm, folder.name)) },
            confirmButton = { TextButton(onClick = { onDelete(); onDismiss() }) { Text(stringResource(R.string.folders_delete)) } },
            dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
        )
        else -> AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(folder.name) },
            text = {
                Column {
                    TextButton(onClick = { renaming = true }) { Text(stringResource(R.string.folders_rename)) }
                    TextButton(onClick = { confirmDelete = true }) { Text(stringResource(R.string.folders_delete)) }
                }
            },
            confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

private const val MAX_NAME = 40
