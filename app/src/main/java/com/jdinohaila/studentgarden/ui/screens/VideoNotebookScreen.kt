// SPDX-License-Identifier: GPL-3.0-or-later
@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.jdinohaila.studentgarden.ui.screens

import android.app.Activity
import android.content.Intent
import android.text.Html
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.jdinohaila.studentgarden.R
import com.jdinohaila.studentgarden.data.GardenRepository
import com.jdinohaila.studentgarden.domain.Note
import com.jdinohaila.studentgarden.export.*
import com.jdinohaila.studentgarden.platform.PrivateMedia
import com.jdinohaila.studentgarden.ui.*
import com.jdinohaila.studentgarden.video.*
import kotlinx.coroutines.*

@Composable
fun NoteExportAction(media: PrivateMedia, enabled: Boolean = true, document: suspend () -> ExportNote) {
    val context = LocalContext.current
    val currentDocument by rememberUpdatedState(document)
    val scope = rememberCoroutineScope()
    var menu by remember { mutableStateOf(false) }
    var format by rememberSaveable { mutableStateOf(NoteFile.PDF.name) }
    var busy by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    var saved by remember { mutableStateOf(false) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) result.data?.data?.let { uri -> scope.launch {
            busy = true; saved = false
            try { NoteExport(context, media).save(currentDocument(), NoteFile.valueOf(format), uri); saved = true }
            catch (e: Exception) { if(e is CancellationException) throw e; failed = true }
            finally { busy = false }
        } }
    }
    Box {
        IconButton(onClick = { menu = true }, enabled = enabled && !busy) {
            if (busy) CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
            else Icon(Icons.Outlined.FileDownload, stringResource(R.string.export_note))
        }
        DropdownMenu(menu, { menu = false }) {
            NoteFile.entries.forEach { type -> DropdownMenuItem(text = { Text(type.label) }, onClick = {
                menu = false; format = type.name
                launcher.launch(Intent(Intent.ACTION_CREATE_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE)
                    .setType(type.mime).putExtra(Intent.EXTRA_TITLE, "Student Garden.${type.extension}"))
            }) }
        }
    }
    if (failed) AlertDialog(onDismissRequest = { failed = false }, text = { Text(stringResource(R.string.export_failed)) },
        confirmButton = { TextButton(onClick = { failed = false }) { Text(stringResource(R.string.close)) } })
    if (saved) {
        LaunchedEffect(Unit) { delay(1800); saved = false }
        // A brief confirmation; there is no persistent explanatory copy in the editor.
        androidx.compose.ui.window.Popup(alignment = Alignment.BottomCenter) {
            Surface(shape = MaterialTheme.shapes.small, color = MaterialTheme.colorScheme.inverseSurface) {
                Text(stringResource(R.string.export_saved), Modifier.padding(16.dp), color = MaterialTheme.colorScheme.inverseOnSurface)
            }
        }
    }
}

@Composable
internal fun FrameNote(imageId: String, at: Long, media: PrivateMedia, onSeek: () -> Unit,
    onRemove: (() -> Unit)? = null, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().testTag("notebook-frame")) {
        AsyncImage(media.file(imageId), stringResource(R.string.video_frame),
            Modifier.fillMaxWidth().aspectRatio(16f/9f), contentScale = ContentScale.Fit)
        TextButton(onClick = onSeek, modifier = Modifier.align(Alignment.BottomStart).testTag("frame-time"),
            colors = ButtonDefaults.textButtonColors(contentColor = Color.White)) {
            Text(VideoNotebook.time(at), style = MaterialTheme.typography.labelLarge.copy(
                shadow = Shadow(Color.Black, Offset(1f, 1f), 5f)))
        }
        if (onRemove != null) IconButton(onClick = onRemove, modifier = Modifier.align(Alignment.TopEnd).testTag("frame-remove")) {
            Box {
                Icon(Icons.Outlined.Close, null, Modifier.offset(1.dp, 1.dp), tint = Color.Black.copy(alpha = .8f))
                Icon(Icons.Outlined.Close, stringResource(R.string.remove_frame_action), tint = Color.White)
            }
        }
    }
}

@Composable
internal fun NotebookEntryView(entry: NotebookEntry, media: PrivateMedia, onSeek: (Long) -> Unit,
    onEdit: (() -> Unit)? = null, onRemove: (() -> Unit)? = null, onRemoveImage: ((String) -> Unit)? = null) {
    val text = remember(entry.html) { Html.fromHtml(entry.html, Html.FROM_HTML_MODE_LEGACY).toString().trim() }
    Column(Modifier.fillMaxWidth().testTag("notebook-entry-${entry.id}"), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        if (entry.images.isEmpty()) Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = { onSeek(entry.positionMillis) }) { Text(VideoNotebook.time(entry.positionMillis)) }
            Spacer(Modifier.weight(1f))
            if (onEdit != null && text.isNotBlank()) IconButton(onClick = onEdit) { Icon(Icons.Outlined.Edit, stringResource(R.string.edit)) }
            if (onRemove != null) IconButton(onClick = onRemove) { Icon(Icons.Outlined.Close, stringResource(R.string.remove_note_entry)) }
        }
        entry.images.forEach { id -> key(id) { FrameNote(id, entry.positionMillis, media, { onSeek(entry.positionMillis) }, onRemoveImage?.let { remove -> { remove(id) } }) } }
        if (text.isNotBlank()) Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.Top) {
            Text(text, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
            if (entry.images.isNotEmpty() && onEdit != null) IconButton(onClick = onEdit) { Icon(Icons.Outlined.Edit, stringResource(R.string.edit)) }
        }
        // Earlier audio remains playable; no new audio-note controls are offered here.
        entry.audio.forEach { id -> key(id) { AudioPlayer(media.file(id)) } }
    }
}

@Composable
fun VideoNotebookScreen(id: String, repo: GardenRepository, media: PrivateMedia, onBack: () -> Unit,
    onDelete: (String) -> Unit, onVideo: (String, Long) -> Unit) {
    val state by repo.state.collectAsStateWithLifecycle()
    val note = state.notes.find { it.id == id } ?: return
    val entries by produceState<List<NotebookEntry>?>(null, note) {
        value = withContext(Dispatchers.IO) { VideoNotebook.entries(note, repo.body(id)) }
    }
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    var editingId by rememberSaveable { mutableStateOf<String?>(null) }
    var draft by rememberSaveable { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }
    val removed = stringResource(R.string.removed)
    val undo = stringResource(R.string.undo)
    fun remove(entry: NotebookEntry, image: String? = null) { scope.launch {
        try {
            if (image == null) repo.removeVideoEntry(id, entry) else repo.removeVideoImage(id, entry, image)
            snackbar.currentSnackbarData?.dismiss()
            if (showDeletionSnackbar(snackbar, removed, undo)) repo.restoreVideoEntry(id, entry)
        } catch (e: Exception) { if(e is CancellationException) throw e; error = e.message.orEmpty() }
    } }
    Scaffold(topBar = { TopAppBar(title = { Text(stringResource(R.string.video_notebook)) }, navigationIcon = { BackButton(onBack) },
        actions = {
            NoteExportAction(media, entries != null) { ExportNote.from(note, repo.body(id)) }
            IconButton(onClick = { onDelete(id) }) { Icon(Icons.Outlined.DeleteOutline, stringResource(R.string.delete)) }
        }) }, snackbarHost = { SnackbarHost(snackbar) }) { padding ->
        LazyColumn(Modifier.fillMaxSize().testTag("video-notebook-page"), contentPadding = pagePadding(padding), verticalArrangement = Arrangement.spacedBy(20.dp)) {
            item(key = "title", contentType = "title") {
                Column {
                    Text(note.title, style = MaterialTheme.typography.headlineMedium)
                    TextButton(onClick = { onVideo(note.videoId!!, note.videoPositionMillis) }) {
                        Icon(Icons.Outlined.PlayCircle, null); Spacer(Modifier.width(8.dp)); Text(stringResource(R.string.watch_video))
                    }
                }
            }
            if (entries == null) item(key = "loading", contentType = "loading") { CircularProgressIndicator() }
            items(entries.orEmpty(), key = { it.id }, contentType = { if (it.images.isEmpty()) "text" else "frame" }) { entry ->
                if (editingId == entry.id) Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(VideoNotebook.time(entry.positionMillis), color = MaterialTheme.colorScheme.primary)
                    OutlinedTextField(draft, { draft = it.take(50000) }, Modifier.fillMaxWidth().testTag("edit-video-entry"),
                        minLines = 4, maxLines = 12, enabled = !busy)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(enabled = draft.isNotBlank() && !busy, onClick = { scope.launch {
                            busy = true
                            try { repo.editVideoEntry(id, entry, draft); editingId = null }
                            catch (e: Exception) { if (e is CancellationException) throw e; error = e.message.orEmpty() }
                            finally { busy = false }
                        } }) { Text(stringResource(R.string.save)) }
                        TextButton(enabled = !busy, onClick = { editingId = null }) { Text(stringResource(R.string.cancel)) }
                    }
                } else NotebookEntryView(entry, media, { onVideo(note.videoId!!, it) }, onEdit = {
                    editingId = entry.id; draft = Html.fromHtml(entry.html, Html.FROM_HTML_MODE_LEGACY).toString().trim()
                }, onRemove = { remove(entry) }, onRemoveImage = { remove(entry, it) })
            }
            if (error.isNotBlank()) item(key = "error", contentType = "error") { Text(error, color = MaterialTheme.colorScheme.error) }
        }
    }
}
