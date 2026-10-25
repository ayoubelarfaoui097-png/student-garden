package com.jdinohaila.studentgarden

import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import com.jdinohaila.studentgarden.ui.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = GardenApp::class)
class NoteDocumentTest {
    @Test fun inlineFormattingSurvivesHtmlSaveAndReload() {
        val doc = NoteDocument("<p>Study this</p>")
        doc.active.editor.selection = TextRange(0, 5)
        doc.change { doc.active.editor.toggleSpanStyle(SpanStyle(fontWeight = FontWeight.Bold)) }
        doc.change { doc.active.editor.toggleSpanStyle(SpanStyle(fontStyle = FontStyle.Italic)) }
        doc.change { doc.active.editor.toggleSpanStyle(SpanStyle(textDecoration = TextDecoration.Underline)) }
        val restored = NoteDocument(doc.toHtml())
        val style = restored.active.editor.getSpanStyle(TextRange(0, 5))
        assertEquals(FontWeight.Bold, style.fontWeight)
        assertEquals(FontStyle.Italic, style.fontStyle)
        assertTrue(style.textDecoration?.contains(TextDecoration.Underline) == true)
        assertEquals("Study this", restored.plainText)
    }

    @Test fun orderedAndBulletedListsSurviveReloadAndCanBeRemoved() {
        val doc = NoteDocument("<p>Read</p>")
        doc.active.editor.toggleOrderedList()
        var restored = NoteDocument(doc.toHtml())
        assertTrue(restored.active.editor.isOrderedList)
        restored.active.editor.toggleOrderedList()
        restored.active.editor.toggleUnorderedList()
        restored = NoteDocument(restored.toHtml())
        assertTrue(restored.active.editor.isUnorderedList)
        restored.active.editor.toggleUnorderedList()
        assertFalse(restored.toHtml().contains("<ul"))
    }

    @Test fun tableInsertionKeepsTextOnBothSidesOfTheCursorAndItsFormatting() {
        val doc = NoteDocument("<p><b>BeforeAfter</b></p>")
        doc.active.editor.selection = TextRange(6)
        doc.saveTable(null, listOf(listOf("Term", "Meaning"), listOf("cat", "animal")))
        assertEquals(listOf("Before", "After"), doc.blocks.filterIsInstance<NoteText>().map { it.editor.annotatedString.text })
        val restored = NoteDocument(doc.toHtml())
        assertEquals(listOf("Before", "After"), restored.blocks.filterIsInstance<NoteText>().map { it.editor.annotatedString.text })
        restored.blocks.filterIsInstance<NoteText>().forEach {
            assertEquals(FontWeight.Bold, it.editor.getSpanStyle(TextRange(0, it.editor.annotatedString.length)).fontWeight)
        }
    }

    @Test fun tableCellsRoundTripSpecialCharactersArabicAndLineBreaks() {
        val values = listOf(listOf("<script> & \"quotes\"", "العائلة"), listOf("line one\nline two\n", ""))
        val restored = NoteDocument(NoteDocument.tableHtml(values))
        assertEquals(values, restored.blocks.filterIsInstance<NoteTable>().single().cells)
        assertTrue(restored.toHtml().contains("&lt;script&gt;"))
    }

    @Test fun undoAndRedoCoverTableEditsDeletionAndTextFormatting() {
        val doc = NoteDocument("<p>Read</p>")
        val original = doc.toHtml()
        doc.active.editor.selection = TextRange(4)
        doc.saveTable(null, listOf(listOf("First")))
        val inserted = doc.toHtml()
        val table = doc.blocks.filterIsInstance<NoteTable>().single()
        doc.saveTable(table.id, listOf(listOf("Edited")))
        val edited = doc.toHtml()
        doc.deleteTable(doc.blocks.filterIsInstance<NoteTable>().single().id)
        doc.undo(); assertEquals(edited, doc.toHtml())
        doc.undo(); assertEquals(inserted, doc.toHtml())
        doc.undo(); assertEquals(original, doc.toHtml())
        doc.redo(); assertEquals(inserted, doc.toHtml())
        doc.redo(); assertEquals(edited, doc.toHtml())
    }

    @Test fun existingRichTableHtmlIsPreservedWhenOnlyTextChanges() {
        val table = "<table class=\"old\"><tr><th>Heading</th><td><b>Keep bold</b></td></tr></table>"
        val doc = NoteDocument("<p>Before</p>" + table + "<p>After</p>")
        doc.active.editor.selection = TextRange(0, 6)
        doc.change { doc.active.editor.toggleSpanStyle(SpanStyle(fontStyle = FontStyle.Italic)) }
        assertTrue(doc.toHtml().contains(table))
        assertEquals(3, doc.blocks.size)
    }
    @Test fun editingOneCellKeepsTheOtherCellsRichHtml() {
        val doc = NoteDocument("<table class=\"old\"><tr><th><b>Keep</b></th><td>Old</td></tr></table>")
        val table = doc.blocks.filterIsInstance<NoteTable>().single()
        doc.saveTable(table.id, listOf(listOf("Keep", "New")))
        assertTrue(doc.toHtml().contains("<th><b>Keep</b></th>"))
        assertTrue(doc.toHtml().contains("class=\"old\""))
        assertEquals("New", NoteDocument(doc.toHtml()).blocks.filterIsInstance<NoteTable>().single().cells[0][1])
    }

}
