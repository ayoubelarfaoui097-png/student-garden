@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.jdinohaila.studentgarden.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.*
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.jdinohaila.studentgarden.R
import com.jdinohaila.studentgarden.ui.theme.readingFamily
import com.mohamedrejeb.richeditor.ui.material3.RichTextEditor
import com.mohamedrejeb.richeditor.ui.material3.RichTextEditorDefaults

@Composable
fun NoteBody(document: NoteDocument, modifier: Modifier = Modifier, attachments: @Composable () -> Unit = {}) {
    var editingIndex by rememberSaveable { mutableIntStateOf(-1) }
    var deleting by remember { mutableStateOf<NoteTable?>(null) }
    LaunchedEffect(document) { snapshotFlow { document.toHtml() }.collect { document.recordChanges() } }
    LaunchedEffect(document.focusRequest) {
        if (document.focusRequest > 0) document.active.focus.requestFocus()
    }
    BoxWithConstraints(modifier.background(MaterialTheme.colorScheme.surface)) {
        val textMinimum = if (document.blocks.size == 1) maxHeight.coerceAtLeast(56.dp) else 56.dp
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
        attachments()
        document.blocks.forEach { block -> key(block.id) {
            when (block) {
                is NoteText -> RichTextEditor(
                    state = block.editor,
                    maxLength = 50000,
                    modifier = Modifier.fillMaxWidth().heightIn(min = textMinimum)
                        .testTag("note-body-${block.id}").focusRequester(block.focus)
                        .onPreviewKeyEvent { event ->
                            if (event.type != KeyEventType.KeyDown || (!event.isCtrlPressed && !event.isMetaPressed)) false
                            else when (event.key) {
                                Key.Z -> { if (event.isShiftPressed) document.redo() else document.undo(); true }
                                Key.Y -> { document.redo(); true }
                                else -> false
                            }
                        }
                        .onFocusChanged { if (it.hasFocus) document.activeId = block.id },
                    placeholder = { Text(stringResource(R.string.write_note)) },
                    textStyle = MaterialTheme.typography.bodyLarge.copy(fontFamily = readingFamily()),
                    colors = RichTextEditorDefaults.richTextEditorColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                        focusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
                        unfocusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
                    ),
                )
                is NoteTable -> Column(Modifier.fillMaxWidth().padding(16.dp).testTag("note-table")) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(stringResource(R.string.table), style = MaterialTheme.typography.titleMedium)
                        Row {
                            IconButton(onClick = { editingIndex = document.blocks.indexOf(block) }) { Icon(Icons.Outlined.Edit, stringResource(R.string.edit_table)) }
                            IconButton(onClick = { deleting = block }) { Icon(Icons.Outlined.DeleteOutline, stringResource(R.string.delete_table)) }
                        }
                    }
                    Column(Modifier.horizontalScroll(rememberScrollState())) {
                        block.cells.forEach { row -> Row(Modifier.height(IntrinsicSize.Min)) {
                            row.forEach { value ->
                                Box(Modifier.width(150.dp).fillMaxHeight().heightIn(min = 48.dp)
                                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant).padding(12.dp)) {
                                    Text(value.ifEmpty { " " })
                                }
                            }
                        } }
                    }
                }
            }
        } }
        Spacer(Modifier.height(24.dp))
        }
    }
    (document.blocks.getOrNull(editingIndex) as? NoteTable)?.let { table ->
        NoteTableDialog(table.cells, onDismiss = { editingIndex = -1 }) {
            document.saveTable(table.id, it); editingIndex = -1
        }
    }
    deleting?.let { table -> AlertDialog(
        onDismissRequest = { deleting = null }, title = { Text(stringResource(R.string.delete_table)) },
        text = { Text(stringResource(R.string.delete_table_hint)) },
        confirmButton = { TextButton(onClick = { document.deleteTable(table.id); deleting = null }) { Text(stringResource(R.string.remove)) } },
        dismissButton = { TextButton(onClick = { deleting = null }) { Text(stringResource(R.string.cancel)) } },
    ) }
}

@Composable
fun NoteTableDialog(initial: List<List<String>> = listOf(listOf("", ""), listOf("", "")), onDismiss: () -> Unit, onSave: (List<List<String>>) -> Unit) {
    val normalized = remember(initial) { initial.map { it + List(initial.maxOf { row -> row.size } - it.size) { "" } } }
    val cellsSaver = Saver<List<List<String>>, String>(save = { Json.encodeToString(it) }, restore = { Json.decodeFromString(it) })
    var cells by rememberSaveable(stateSaver = cellsSaver) { mutableStateOf(normalized) }
    var remove by rememberSaveable { mutableStateOf<String?>(null) }
    var discard by rememberSaveable { mutableStateOf(false) }
    fun leave() { if (cells != normalized) discard = true else onDismiss() }
    val columns = cells.maxOf { it.size }
    fun removeLast(kind: String) {
        cells = if (kind == "row") cells.dropLast(1) else cells.map { it.dropLast(1) }
    }
    Dialog(onDismissRequest = { leave() }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Scaffold(
            modifier = Modifier.fillMaxSize().imePadding(),
            topBar = { TopAppBar(title = { Text(stringResource(R.string.table)) }, navigationIcon = { BackButton { leave() } },
                actions = { TextButton(onClick = { onSave(cells) }) { Text(stringResource(R.string.save)) } }) },
        ) { p -> Column(Modifier.fillMaxSize().padding(p).consumeWindowInsets(p).padding(16.dp)) {
            Text(stringResource(R.string.table_hint), style = MaterialTheme.typography.bodySmall)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = { cells = cells + listOf(List(columns) { "" }) }, enabled = cells.size < 30) { Text(stringResource(R.string.add_row)) }
                TextButton(onClick = { cells = cells.map { it + "" } }, enabled = columns < 6) { Text(stringResource(R.string.add_column)) }
                TextButton(onClick = {
                    if (cells.last().any { it.isNotEmpty() }) remove = "row" else removeLast("row")
                }, enabled = cells.size > 1) { Text(stringResource(R.string.remove_row)) }
                TextButton(onClick = {
                    if (cells.any { it.last().isNotEmpty() }) remove = "column" else removeLast("column")
                }, enabled = columns > 1) { Text(stringResource(R.string.remove_column)) }
            }
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).horizontalScroll(rememberScrollState())) {
                cells.forEachIndexed { r, row -> Row {
                    row.forEachIndexed { c, value ->
                        val label = stringResource(R.string.table_cell, r + 1, c + 1)
                        OutlinedTextField(value, { text -> cells = cells.mapIndexed { ri, old ->
                            if (ri != r) old else old.mapIndexed { ci, previous -> if (ci == c) text.take(minOf(5000, (50000 - cells.sumOf { row -> row.sumOf { it.length } } + value.length).coerceAtLeast(0))) else previous }
                        } }, Modifier.width(160.dp).padding(2.dp).testTag("table-cell-$r-$c").semantics { contentDescription = label },
                            label = { Text(label) }, minLines = 2, maxLines = 5)
                    }
                } }
            }
        } }
        if (discard) AlertDialog(onDismissRequest = { discard = false },
            title = { Text(stringResource(R.string.discard)) },
            confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.close)) } },
            dismissButton = { TextButton(onClick = { discard = false }) { Text(stringResource(R.string.keep_editing)) } })
        remove?.let { kind -> AlertDialog(onDismissRequest = { remove = null },
            title = { Text(stringResource(R.string.remove_table_data)) },
            text = { Text(stringResource(R.string.remove_table_data_hint)) },
            confirmButton = { TextButton(onClick = { removeLast(kind); remove = null }) { Text(stringResource(R.string.remove)) } },
            dismissButton = { TextButton(onClick = { remove = null }) { Text(stringResource(R.string.cancel)) } }) }
    }
}
