package com.eyal98.stickerfinder.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.eyal98.stickerfinder.R
import com.eyal98.stickerfinder.data.FaceOnSticker
import com.eyal98.stickerfinder.data.PersonSummary

/** Groups of the same person's face across stickers, which the user names to make searchable. */
@Composable
fun PeopleScreen(onBack: () -> Unit, viewModel: PeopleViewModel = viewModel(factory = PeopleViewModel.Factory)) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val selectedId by viewModel.selectedId.collectAsStateWithLifecycle()
    val selected = state.people.firstOrNull { it.id == selectedId }
    BackHandler(onBack = if (selectedId != null) ({ viewModel.select(null) }) else onBack)
    Scaffold { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            if (selected != null) {
                PersonDetail(selected, state.people, viewModel)
            } else {
                Overview(state, viewModel, onBack)
            }
        }
    }
}

@Composable
private fun Overview(state: PeopleUiState, viewModel: PeopleViewModel, onBack: () -> Unit) {
    val context = LocalContext.current
    var confirmDelete by remember { mutableStateOf(false) }
    // Notifications first (Android 13+), so the run shows its progress; it starts either way.
    val askNotifications = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        viewModel.startNow()
    }
    val startNow = {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            askNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            viewModel.startNow()
        }
    }
    LazyVerticalGrid(
        columns = GridCells.Adaptive(104.dp),
        contentPadding = PaddingValues(16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                TextButton(onClick = onBack) { Text(stringResource(R.string.back)) }
                Text(stringResource(R.string.people_title), style = MaterialTheme.typography.headlineSmall)
                Text(stringResource(R.string.people_intro), style = MaterialTheme.typography.bodyLarge)
                Text(stringResource(R.string.people_privacy), style = MaterialTheme.typography.bodyMedium)
                when {
                    !state.available -> Text(stringResource(R.string.people_unavailable), color = MaterialTheme.colorScheme.error)
                    !state.enabled -> Button(onClick = {
                        viewModel.turnOn()
                        startNow()
                    }) { Text(stringResource(R.string.people_turn_on)) }
                    else -> {
                        if (state.pending > 0) {
                            Text(stringResource(R.string.people_scanning, state.pending, state.total))
                            if (state.running) {
                                Text(stringResource(R.string.image_tags_running), color = MaterialTheme.colorScheme.primary)
                            } else {
                                Text(stringResource(R.string.image_tags_waiting), style = MaterialTheme.typography.bodyMedium)
                                Button(onClick = startNow) { Text(stringResource(R.string.image_tags_start)) }
                            }
                        } else {
                            Text(stringResource(R.string.people_done))
                        }
                        if (state.people.isEmpty()) Text(stringResource(R.string.people_none))
                        OutlinedButton(onClick = { confirmDelete = true }) { Text(stringResource(R.string.people_delete)) }
                    }
                }
            }
        }
        if (state.enabled) {
            items(state.people, key = { it.id }) { person ->
                PersonCard(person, viewModel) { viewModel.select(person.id) }
            }
        }
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            text = { Text(stringResource(R.string.people_delete_confirm)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    viewModel.turnOffAndDelete()
                }) { Text(stringResource(R.string.people_delete_yes)) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

@Composable
private fun PersonCard(person: PersonSummary, viewModel: PeopleViewModel, onClick: () -> Unit) {
    val cover by remember(person.id) { viewModel.cover(person.id) }.collectAsStateWithLifecycle(emptyList())
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.clickable(onClick = onClick),
    ) {
        Surface(shape = RoundedCornerShape(12.dp), tonalElevation = 2.dp, modifier = Modifier.fillMaxWidth().aspectRatio(1f)) {
            cover.firstOrNull()?.let { FaceCrop(it, Modifier.fillMaxSize()) }
        }
        Text(
            person.name ?: stringResource(R.string.people_unnamed),
            style = MaterialTheme.typography.titleSmall,
            color = if (person.name == null) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
        )
        Text(stringResource(R.string.people_count, person.stickers), style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun PersonDetail(person: PersonSummary, all: List<PersonSummary>, viewModel: PeopleViewModel) {
    val faces by viewModel.faces.collectAsStateWithLifecycle()
    var name by rememberSaveable(person.id) { mutableStateOf(person.name.orEmpty()) }
    var removing by remember { mutableStateOf<FaceOnSticker?>(null) }
    var merging by remember { mutableStateOf(false) }
    LazyVerticalGrid(
        columns = GridCells.Adaptive(88.dp),
        contentPadding = PaddingValues(16.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                TextButton(onClick = { viewModel.select(null) }) { Text(stringResource(R.string.back)) }
                Text(stringResource(R.string.people_count, person.stickers), style = MaterialTheme.typography.titleMedium)
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    placeholder = { Text(stringResource(R.string.people_name_hint)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { viewModel.rename(person.id, name) }, enabled = name.trim() != person.name.orEmpty()) {
                        Text(stringResource(R.string.people_save))
                    }
                    if (all.size > 1) OutlinedButton(onClick = { merging = true }) { Text(stringResource(R.string.people_merge)) }
                }
                Text(stringResource(R.string.people_remove_hint), style = MaterialTheme.typography.bodyMedium)
            }
        }
        items(faces, key = { it.id }) { face ->
            FaceCrop(
                face,
                Modifier.aspectRatio(1f).clip(RoundedCornerShape(8.dp)).clickable { removing = face },
            )
        }
    }
    removing?.let { face ->
        AlertDialog(
            onDismissRequest = { removing = null },
            text = {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    FaceCrop(face, Modifier.size(120.dp))
                    Text(stringResource(R.string.people_remove_confirm), modifier = Modifier.padding(top = 12.dp))
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.removeFace(face.id)
                    removing = null
                }) { Text(stringResource(R.string.people_remove)) }
            },
            dismissButton = { TextButton(onClick = { removing = null }) { Text(stringResource(R.string.cancel)) } },
        )
    }
    if (merging) {
        AlertDialog(
            onDismissRequest = { merging = false },
            title = { Text(stringResource(R.string.people_merge_title)) },
            text = {
                LazyColumn(Modifier.heightIn(max = 400.dp)) {
                    items(all.filter { it.id != person.id }, key = { it.id }) { other ->
                        val cover by remember(other.id) { viewModel.cover(other.id) }.collectAsStateWithLifecycle(emptyList())
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    viewModel.merge(from = person.id, into = other.id)
                                    merging = false
                                }
                                .padding(vertical = 6.dp),
                        ) {
                            cover.firstOrNull()?.let { FaceCrop(it, Modifier.size(48.dp).clip(RoundedCornerShape(8.dp))) }
                            Text(
                                (other.name ?: stringResource(R.string.people_unnamed)) + " · " +
                                    stringResource(R.string.people_count, other.stickers),
                                modifier = Modifier.padding(start = 12.dp),
                            )
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { merging = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}
