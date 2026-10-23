package com.jdinohaila.studentgarden

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.unit.sp
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.test.core.app.ApplicationProvider
import com.jdinohaila.studentgarden.domain.*
import com.jdinohaila.studentgarden.ui.*
import com.jdinohaila.studentgarden.ui.screens.NoteEditor
import com.jdinohaila.studentgarden.ui.theme.GardenTheme
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.*

@OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w360dp-h800dp-xhdpi", application = GardenApp::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class NoteEditorUiTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val restoration = StateRestorationTester(compose)
    private lateinit var app: GardenApp
    @Before fun setup() = runBlocking {
        app = ApplicationProvider.getApplicationContext()
        app.deleteDatabase("garden.db")
        app.repository.load()
        restoration.setContent { GardenTheme {
            NoteEditor("c92391b2-2806-4a7f-a869-7754c3e791df", null, app.repository, {}, { note, html -> app.repository.save(note, html) }, {})
        } }
        compose.onNodeWithText("Title").performTextInput("My study note")
    }

    private fun image(name: String) {
        compose.waitForIdle()
        compose.runOnIdle {
            val view = compose.activity.window.decorView
            val bitmap = android.graphics.Bitmap.createBitmap(view.width, view.height, android.graphics.Bitmap.Config.ARGB_8888)
            view.draw(android.graphics.Canvas(bitmap))
            val output = java.io.File("build/qa/$name.png")
            output.parentFile!!.mkdirs()
            output.outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
        }
    }

    private fun saveDocument(): NoteDocument {
        compose.onNodeWithText("Save").performClick()
        compose.waitUntil(5000) { app.repository.state.value.notes.isNotEmpty() }
        return NoteDocument(runBlocking { app.repository.body("c92391b2-2806-4a7f-a869-7754c3e791df") })
    }

    @Test fun boldItalicAndUnderlineApplyToSelectedTextAndPersist() {
        val body = compose.onNodeWithText("Start writing…")
        body.performTextInput("Study this")
        compose.onNodeWithText("Study this").performTextInputSelection(TextRange(0, 5))
        listOf("Bold", "Italic", "Underline").forEach {
            compose.onNodeWithContentDescription(it).performClick().assertIsOn()
        }
        image("note-selected-formatting")
        val style = saveDocument().active.editor.getSpanStyle(TextRange(0, 5))
        assertEquals(FontWeight.Bold, style.fontWeight)
        assertEquals(FontStyle.Italic, style.fontStyle)
        assertTrue(style.textDecoration?.contains(TextDecoration.Underline) == true)
    }

    @Test fun choosingFormatsBeforeTypingCombinesStylesAndCanToggleThemOff() {
        compose.onNodeWithContentDescription("Bold").performClick().assertIsOn()
        compose.onNodeWithContentDescription("Bold").performClick().assertIsOff()
        compose.onNodeWithContentDescription("Bold").performClick().assertIsOn()
        compose.onNodeWithContentDescription("Italic").performClick().assertIsOn()
        compose.onNodeWithContentDescription("Bold").assertIsOn()
        compose.onNodeWithText("Start writing…").performTextInput("Remember")
        val style = saveDocument().active.editor.getSpanStyle(TextRange(0, 8))
        assertEquals(FontWeight.Bold, style.fontWeight)
        assertEquals(FontStyle.Italic, style.fontStyle)
    }

    @Test fun formattingCanBeUndoneRedoneAndCleared() {
        compose.onNodeWithText("Start writing…").performTextInput("Recall")
        compose.onNodeWithText("Recall").performTextInputSelection(TextRange(0, 6))
        compose.onNodeWithContentDescription("Bold").performClick()
        compose.onNodeWithContentDescription("Undo").performClick()
        compose.onNodeWithContentDescription("Bold").assertIsOff()
        compose.onNodeWithContentDescription("Redo").assertIsEnabled().performClick()
        compose.onNodeWithText("Recall").performTextInputSelection(TextRange(0, 6))
        compose.onNodeWithContentDescription("Bold").assertIsOn()
        compose.onNodeWithContentDescription("Clear formatting").performClick()
        assertNotEquals(FontWeight.Bold, saveDocument().active.editor.getSpanStyle(TextRange(0, 6)).fontWeight)
    }

    @Test fun boldWorksAcrossMixedSelectionsAndRemainsIndependentOfHeadingSize() {
        compose.onNodeWithText("Start writing…").performTextInput("Bold and plain")
        compose.onNodeWithText("Bold and plain").performTextInputSelection(TextRange(0, 4))
        compose.onNodeWithContentDescription("Bold").performClick().assertIsOn()
        compose.onNodeWithText("Bold and plain").performTextInputSelection(TextRange(0, 14))
        compose.onNodeWithContentDescription("Bold").performClick().assertIsOn()
        compose.onNodeWithContentDescription("Heading").performClick().assertIsOn()
        compose.onNodeWithContentDescription("Bold").assertIsOn()
        compose.onNodeWithContentDescription("Heading").performClick().assertIsOff()
        compose.onNodeWithContentDescription("Bold").assertIsOn()
        val style = saveDocument().active.editor.getSpanStyle(TextRange(0, 14))
        assertEquals(FontWeight.Bold, style.fontWeight)
    }

    @Test fun tableAtTheCursorSurvivesDraftRestorationAndSaving() {
        compose.onNodeWithText("Start writing…").performTextInput("BeforeAfter")
        compose.onNodeWithText("BeforeAfter").performTextInputSelection(TextRange(6))
        compose.onNodeWithContentDescription("Insert table").assertIsDisplayed().performClick()
        compose.onNodeWithTag("table-cell-0-0").performTextInput("Term")
        compose.onNodeWithTag("table-cell-0-1").performTextInput("Meaning")
        compose.onNodeWithTag("table-cell-1-0").performTextInput("cat")
        compose.onNodeWithTag("table-cell-1-1").performTextInput("animal & friend\nA companion in the family")
        compose.onNode(hasText("Save") and hasAnyAncestor(isDialog())).performClick()
        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithTag("note-table").performScrollTo().assertIsDisplayed()
        image("note-table-editor")
        val document = saveDocument()
        assertEquals(listOf("Before", "After"), document.blocks.filterIsInstance<NoteText>().map { it.editor.annotatedString.text })
        assertEquals("animal & friend\nA companion in the family", document.blocks.filterIsInstance<NoteTable>().single().cells[1][1])
    }

    @Test fun headingsAndListsHaveVisibleSelectedStatesAndPersist() {
        compose.onNodeWithText("Start writing…").performTextInput("Important")
        compose.onNodeWithText("Important").performTextInputSelection(TextRange(0, 9))
        compose.onNodeWithContentDescription("Heading").performClick().assertIsOn()
        compose.onNodeWithContentDescription("Bulleted list").performClick().assertIsOn()
        compose.onNodeWithContentDescription("Numbered list").performClick().assertIsOn()
        compose.onNodeWithContentDescription("Bulleted list").assertIsOff()
        val document = saveDocument()
        assertTrue(document.active.editor.isOrderedList)
        val start = document.active.editor.annotatedString.text.indexOf("Important")
        assertEquals(24.sp, document.active.editor.getSpanStyle(TextRange(start, start + 9)).fontSize)
    }
    @Test fun tableDraftAndResizeControlsProtectFilledCells() {
        compose.onNodeWithContentDescription("Insert table").performClick()
        compose.onNodeWithTag("table-cell-1-0").performTextInput("Keep row")
        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithTag("table-cell-1-0").assertTextContains("Keep row")
        compose.onNodeWithText("Remove last row").performClick()
        compose.onNodeWithText("Remove filled cells?").assertIsDisplayed()
        compose.onNodeWithText("Cancel").performClick()
        compose.onNodeWithTag("table-cell-1-0").assertExists()
        compose.onNodeWithText("Add row").performClick()
        compose.onNodeWithTag("table-cell-2-0").assertExists()
        compose.onNodeWithText("Add column").performClick()
        compose.onNodeWithTag("table-cell-0-2").assertExists()
        compose.onNodeWithText("Remove last column").performClick()
        compose.onNodeWithTag("table-cell-0-2").assertDoesNotExist()
        compose.onNode(hasText("Save") and hasAnyAncestor(isDialog())).performClick()
        compose.onNodeWithContentDescription("Delete table").performScrollTo().performClick()
        compose.onNodeWithText("Remove").performClick()
        compose.onNodeWithTag("note-table").assertDoesNotExist()
        compose.onNodeWithContentDescription("Undo").performClick()
        compose.onNodeWithTag("note-table").assertExists()
        val table = saveDocument().blocks.filterIsInstance<NoteTable>().single()
        assertEquals(3, table.cells.size)
        assertEquals(2, table.cells[0].size)
        assertEquals("Keep row", table.cells[1][0])
    }

    @Test fun keyboardUndoAndToolbarRedoUseTheSameHistory() {
        compose.onNodeWithText("Start writing…").performTextInput("Recall")
        compose.onNodeWithText("Recall").performTextInputSelection(TextRange(0, 6))
        compose.onNodeWithContentDescription("Bold").performClick()
        compose.onNodeWithText("Recall").performKeyInput {
            keyDown(Key.CtrlLeft); pressKey(Key.Z); keyUp(Key.CtrlLeft)
        }
        compose.onNodeWithContentDescription("Bold").assertIsOff()
        compose.onNodeWithContentDescription("Redo").assertIsEnabled().performClick()
        compose.onNodeWithText("Recall").performTextInputSelection(TextRange(0, 6))
        assertEquals(FontWeight.Bold, saveDocument().active.editor.getSpanStyle(TextRange(0, 6)).fontWeight)
    }

}
