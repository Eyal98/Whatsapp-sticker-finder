package com.eyal98.stickerfinder.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.eyal98.stickerfinder.R
import com.eyal98.stickerfinder.data.ImageTagFilter
import com.eyal98.stickerfinder.data.StickerFaceInfo

private enum class Picker { LOOKS, CONTEXT }

/** A sticker's editable fields, and sharing an edit with similar stickers. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun StickerDetailsScreen(
    stickerId: Long,
    onBack: () -> Unit,
    viewModel: StickerDetailsViewModel = viewModel(key = "details-$stickerId", factory = StickerDetailsViewModel.factory(stickerId)),
) {
    val sticker by viewModel.sticker.collectAsStateWithLifecycle()
    val faces by viewModel.faces.collectAsStateWithLifecycle()
    val state by viewModel.state.collectAsStateWithLifecycle()
    var picker by remember { mutableStateOf<Picker?>(null) }
    BackHandler(onBack = onBack)
    Scaffold { padding ->
        val s = sticker
        if (s == null || !state.loaded) return@Scaffold
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            ScreenHeader(stringResource(R.string.details_title), onBack) {
                Button(onClick = { viewModel.save(onBack) }, enabled = !state.saving) {
                    Text(
                        if (state.shareCount > 0) stringResource(R.string.details_save_share, state.shareCount)
                        else stringResource(R.string.save),
                    )
                }
            }
            Surface(
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                modifier = Modifier.size(180.dp).align(Alignment.CenterHorizontally),
            ) {
                StickerThumbnail(s.documentUri, null, Modifier.fillMaxSize().padding(12.dp))
            }
            s.packName?.let { Labeled(stringResource(R.string.details_pack), it) }
            s.ocrText?.takeIf { it.isNotBlank() }?.let { Labeled(stringResource(R.string.details_printed_text), it) }

            // Your tags, as removable chips like the picture tags, plus a field to add one.
            Text(stringResource(R.string.tags_title), style = MaterialTheme.typography.titleSmall)
            if (state.tags.isNotEmpty()) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    for (tag in state.tags) {
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.secondaryContainer,
                            modifier = Modifier.clickable { viewModel.removeTag(tag) },
                        ) {
                            Text(
                                "$tag ✕",
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            )
                        }
                    }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = state.newTag,
                    onValueChange = viewModel::setNewTag,
                    placeholder = { Text(stringResource(R.string.details_add_tag_hint)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { viewModel.addTag() }),
                    modifier = Modifier.weight(1f),
                )
                OutlinedButton(onClick = viewModel::addTag, enabled = state.newTag.isNotBlank()) {
                    Text(stringResource(R.string.details_add_tag))
                }
            }
            OutlinedTextField(
                value = state.description,
                onValueChange = viewModel::setDescription,
                label = { Text(stringResource(R.string.details_description)) },
                placeholder = { Text(stringResource(R.string.details_description_hint)) },
                minLines = 2,
                modifier = Modifier.fillMaxWidth(),
            )

            // Picture tags: tap to hide a wrong one, tap again to bring it back.
            val pictureTags = ImageTagFilter.split(s.imageTags)
            if (pictureTags.isNotEmpty()) {
                Text(stringResource(R.string.details_picture_tags), style = MaterialTheme.typography.titleSmall)
                Text(stringResource(R.string.details_picture_tags_hint), style = MaterialTheme.typography.bodySmall)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    for (tag in pictureTags) {
                        val hidden = state.removedPictureTags.any { it.equals(tag, ignoreCase = true) }
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = if (hidden) MaterialTheme.colorScheme.surfaceContainer else MaterialTheme.colorScheme.primaryContainer,
                            modifier = Modifier.clickable {
                                if (hidden) state.removedPictureTags.filter { it.equals(tag, true) }.forEach(viewModel::showPictureTag)
                                else viewModel.hidePictureTag(tag)
                            },
                        ) {
                            Text(
                                if (hidden) "$tag ↺" else "$tag ✕",
                                textDecoration = if (hidden) TextDecoration.LineThrough else null,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp).alpha(if (hidden) 0.5f else 1f),
                            )
                        }
                    }
                }
            }

            if (faces.isNotEmpty()) {
                Text(stringResource(R.string.details_people), style = MaterialTheme.typography.titleSmall)
                for (face in faces) FaceRow(face, viewModel)
            }

            HorizontalDivider()
            Text(stringResource(R.string.details_share_title), style = MaterialTheme.typography.titleMedium)
            Text(stringResource(R.string.details_share_body), style = MaterialTheme.typography.bodySmall)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { viewModel.loadLooks(); picker = Picker.LOOKS }) {
                    Text(stringResource(R.string.details_share_looks, state.looks.selected.size))
                }
                OutlinedButton(onClick = { viewModel.loadContext(); picker = Picker.CONTEXT }) {
                    Text(stringResource(R.string.details_share_context, state.context.selected.size))
                }
            }
            if (state.samePerson.isNotEmpty()) {
                CheckRow(stringResource(R.string.details_share_person, state.samePerson.size), state.shareSamePerson, viewModel::setShareSamePerson)
            }
            if (state.packSize > 1) {
                CheckRow(stringResource(R.string.details_share_pack, state.packSize - 1), state.sharePack, viewModel::setSharePack)
            }
        }
    }
    when (picker) {
        Picker.LOOKS -> SharePicker(
            title = stringResource(R.string.details_looks_title),
            set = state.looks,
            onToggle = viewModel::toggleLook,
            onDone = { picker = null },
        )
        Picker.CONTEXT -> SharePicker(
            title = stringResource(R.string.details_context_title),
            set = state.context,
            onToggle = viewModel::toggleContext,
            onDone = { picker = null },
        )
        null -> Unit
    }
}

@Composable
private fun FaceRow(face: StickerFaceInfo, viewModel: StickerDetailsViewModel) {
    var name by rememberSaveable(face.id, face.name) { mutableStateOf(face.name.orEmpty()) }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FaceCrop(face.asFace(), Modifier.size(56.dp).clip(RoundedCornerShape(8.dp)))
        val personId = face.personId
        if (personId == null) {
            Text(stringResource(R.string.details_face_ungrouped), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        } else {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                placeholder = { Text(stringResource(R.string.people_unnamed)) },
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
            if (name.trim() != face.name.orEmpty()) {
                TextButton(onClick = { viewModel.renamePerson(personId, name) }) { Text(stringResource(R.string.save)) }
            }
            TextButton(onClick = { viewModel.removeFace(face.id) }) { Text(stringResource(R.string.details_not_them)) }
        }
    }
}

@Composable
private fun CheckRow(text: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { onChange(!checked) }) {
        Checkbox(checked = checked, onCheckedChange = onChange)
        Text(text, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun Labeled(label: String, value: String) {
    Column {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}

/** A grid of candidate stickers to share the edit with; tap to pick or unpick. */
@Composable
private fun SharePicker(title: String, set: ShareSet, onToggle: (Long) -> Unit, onDone: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDone,
        title = { Text(title) },
        text = {
            val candidates = set.candidates
            when {
                candidates == null -> Text(stringResource(R.string.details_loading))
                candidates.isEmpty() -> Text(stringResource(R.string.details_no_candidates))
                else -> LazyVerticalGrid(
                    columns = GridCells.Adaptive(80.dp),
                    contentPadding = PaddingValues(top = 4.dp),
                    modifier = Modifier.height(380.dp),
                ) {
                    items(candidates, key = { it.sticker.id }) { c ->
                        val picked = c.sticker.id in set.selected
                        Box(
                            Modifier
                                .aspectRatio(1f)
                                .padding(3.dp)
                                .border(
                                    width = if (picked) 3.dp else 1.dp,
                                    color = if (picked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                                    shape = RoundedCornerShape(8.dp),
                                )
                                .clickable { onToggle(c.sticker.id) },
                        ) {
                            StickerThumbnail(
                                c.sticker.documentUri,
                                null,
                                Modifier.fillMaxSize().padding(4.dp).alpha(if (picked) 1f else 0.5f),
                            )
                            if (picked) {
                                Text("✓", color = MaterialTheme.colorScheme.primary, modifier = Modifier.align(Alignment.TopEnd).padding(end = 6.dp))
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDone) { Text(stringResource(R.string.details_done, set.selected.size)) } },
    )
}
