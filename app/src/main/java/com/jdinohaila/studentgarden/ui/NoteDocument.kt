package com.jdinohaila.studentgarden.ui

import android.os.SystemClock
import android.text.Html
import android.text.TextUtils
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.text.TextRange
import com.mohamedrejeb.richeditor.model.RichTextState
import java.util.UUID

sealed class NoteBlock(val id: String = UUID.randomUUID().toString())
class NoteText(html: String = "") : NoteBlock() {
    val editor = RichTextState().apply { setHtml(html) }
    val focus = FocusRequester()
}
class NoteTable(val cells: List<List<String>>, val originalHtml: String? = null) : NoteBlock()

/** Native text and table blocks share the existing portable HTML note body. */
class NoteDocument(html: String = "") {
    val blocks = mutableStateListOf<NoteBlock>()
    var activeId by mutableStateOf("")
    var focusRequest by mutableIntStateOf(0)
        private set
    private val past = mutableStateListOf<String>()
    private val future = mutableStateListOf<String>()
    private var previous = ""
    private var changedAt = 0L
    private var applyingChange = false
    val active: NoteText get() = blocks.filterIsInstance<NoteText>().firstOrNull { it.id == activeId }
        ?: blocks.filterIsInstance<NoteText>().first()
    val canUndo get() = past.isNotEmpty() || toHtml() != previous
    val canRedo get() = future.isNotEmpty()
    val plainText get() = blocks.joinToString("\n") {
        when (it) {
            is NoteText -> it.editor.annotatedString.text
            is NoteTable -> it.cells.joinToString("\n") { row -> row.joinToString("\t") }
        }
    }

    init { setHtml(html) }

    fun setHtml(html: String) {
        load(html)
        previous = toHtml()
        past.clear()
        future.clear()
    }

    private fun load(html: String) {
        val parsed = mutableListOf<NoteBlock>()
        var offset = 0
        tablePattern.findAll(html).forEach { match ->
            val text = html.substring(offset, match.range.first)
            if (text.isNotBlank()) parsed += NoteText(text)
            val rows = rowPattern.findAll(match.value).map { row ->
                cellPattern.findAll(row.value).map {
                    Html.fromHtml(it.groupValues[1], Html.FROM_HTML_MODE_LEGACY).toString()
                }.toList()
            }.filter { it.isNotEmpty() }.toList()
            // Preserve unknown or richly formatted table HTML until explicitly edited.
            parsed += NoteTable(rows.ifEmpty { listOf(listOf("")) }, match.value)
            offset = match.range.last + 1
        }
        if (offset < html.length) parsed += NoteText(html.substring(offset))
        if (parsed.lastOrNull() !is NoteText) parsed += NoteText()
        Snapshot.withMutableSnapshot {
            blocks.clear()
            blocks.addAll(parsed)
            activeId = parsed.filterIsInstance<NoteText>().first().id
        }
    }

    fun toHtml(): String = blocks.joinToString("") {
        when (it) {
            is NoteText -> it.editor.toHtml()
            is NoteTable -> it.originalHtml ?: tableHtml(it.cells)
        }
    }

    /** Coalesce typing while keeping toolbar and table operations separate. */
    fun recordChanges(force: Boolean = false) {
        if (applyingChange) return
        val now = SystemClock.elapsedRealtime()
        val current = toHtml()
        if (current == previous) return
        if (force || past.isEmpty() || now - changedAt > 500) {
            past += previous
            if (past.size > 60) past.removeAt(0)
        }
        future.clear()
        previous = current
        changedAt = now
    }

    fun change(action: () -> Unit) {
        recordChanges()
        applyingChange = true
        try {
            Snapshot.withMutableSnapshot {
                val before = toHtml()
                action()
                val after = toHtml()
                if (after != before) {
                    past += before
                    if (past.size > 60) past.removeAt(0)
                    future.clear()
                    previous = after
                    changedAt = SystemClock.elapsedRealtime()
                }
            }
        } finally { applyingChange = false }
    }

    fun undo() {
        recordChanges()
        if (past.isEmpty()) return
        applyingChange = true
        try {
            Snapshot.withMutableSnapshot {
                future += toHtml()
                val html = past.removeAt(past.lastIndex)
                load(html)
                previous = toHtml()
                focusRequest++
            }
        } finally { applyingChange = false }
    }

    fun redo() {
        recordChanges()
        if (future.isEmpty()) return
        applyingChange = true
        try {
            Snapshot.withMutableSnapshot {
                past += toHtml()
                val html = future.removeAt(future.lastIndex)
                load(html)
                previous = toHtml()
                focusRequest++
            }
        } finally { applyingChange = false }
    }

    fun saveTable(id: String?, cells: List<List<String>>) = change {
        val index = blocks.indexOfFirst { it.id == id }
        if (index >= 0) {
            val old = blocks[index] as NoteTable
            if (old.cells != cells) blocks[index] = NoteTable(cells, updatedTableHtml(old, cells))
        }
        else {
            val text = active
            val insertion = blocks.indexOf(text)
            val cursor = text.editor.selection.max
            val before = text.editor.copy().apply {
                removeTextRange(TextRange(cursor, annotatedString.length))
            }
            val after = text.editor.copy().apply { removeTextRange(TextRange(0, cursor)) }
            blocks[insertion] = NoteText(before.toHtml())
            blocks.add(insertion + 1, NoteTable(cells))
            val following = NoteText(after.toHtml())
            blocks.add(insertion + 2, following)
            activeId = following.id
            focusRequest++
        }
    }

    fun deleteTable(id: String) = change { blocks.removeAll { it.id == id } }

    companion object {
        private val tablePattern = Regex("<table\\b[^>]*>.*?</table\\s*>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
        private val rowPattern = Regex("<tr\\b[^>]*>(.*?)</tr\\s*>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
        private val cellPattern = Regex("<t[dh]\\b[^>]*>(.*?)</t[dh]\\s*>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
        private fun updatedTableHtml(old: NoteTable, cells: List<List<String>>): String? {
            val html = old.originalHtml ?: return null
            if (old.cells.map { it.size } != cells.map { it.size }) return null
            if (rowPattern.findAll(html).map { cellPattern.findAll(it.value).count() }.toList() != old.cells.map { it.size }) return null
            var r = 0
            return rowPattern.replace(html) { row ->
                val currentRow = r++
                var c = 0
                cellPattern.replace(row.value) { cell ->
                    val currentColumn = c++
                    if (old.cells[currentRow][currentColumn] == cells[currentRow][currentColumn]) cell.value
                    else cell.value.substringBefore(">") + ">" + cellHtml(cells[currentRow][currentColumn]) +
                        cell.value.substring(cell.value.lastIndexOf("</"))
                }
            }
        }
        private fun cellHtml(value: String) = TextUtils.htmlEncode(value).replace("\n", "<br>")
        fun tableHtml(cells: List<List<String>>) = "<table border=\"1\"><tbody>" + cells.joinToString("") { row ->
            "<tr>" + row.joinToString("") { "<td>" + cellHtml(it) + "</td>" } + "</tr>"
        } + "</tbody></table>"
        val Saver = Saver<NoteDocument, String>(save = { it.toHtml() }, restore = { NoteDocument(it) })
    }
}
