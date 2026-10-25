package com.jdinohaila.studentgarden

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.test.core.app.ApplicationProvider
import com.jdinohaila.studentgarden.domain.*
import com.jdinohaila.studentgarden.ui.screens.NoteEditor
import com.jdinohaila.studentgarden.ui.theme.GardenTheme
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.*

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w360dp-h800dp-xhdpi", application = GardenApp::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class AudioNoteUiTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private lateinit var app: GardenApp
    @Before fun setup() = runBlocking {
        app = ApplicationProvider.getApplicationContext()
        app.deleteDatabase("garden.db"); app.repository.load()
    }
    @Test fun recordsAnAudioOnlyNoteWithWaveformAndBlocksSavingUntilStop() {
        org.robolectric.Shadows.shadowOf(app).grantPermissions(android.Manifest.permission.RECORD_AUDIO)
        val id = newId()
        compose.setContent { GardenTheme { NoteEditor(id, null, app.repository, {}, { note, html -> app.repository.save(note, html) }, {}) } }
        compose.onNodeWithText("Title").performTextInput("Voice revision")
        compose.onNodeWithContentDescription("Audio notes").performClick()
        compose.onNodeWithText("Record audio").performScrollTo().performClick()
        compose.onNodeWithTag("recording-waveform").assertIsDisplayed()
        compose.onNodeWithText("Save").assertIsNotEnabled()
        compose.onNode(hasText("Stop recording") and hasAnyAncestor(hasTestTag("recording-status"))).performClick()
        compose.onNodeWithTag("recording-status").assertDoesNotExist()
        compose.onNodeWithText("Audio 1").assertExists()
        compose.onNodeWithText("Save").performClick()
        compose.waitUntil(5000) { app.repository.state.value.notes.isNotEmpty() }
        assertEquals(1, app.repository.state.value.notes.single().media.size)
    }
    @Test fun audioRemovalAndVideoSourceSurviveDraftRestorationWithoutChangingWrittenText() = runBlocking {
        val first = "${newId()}.m4a"; val second = "${newId()}.m4a"
        val note = Note(title = "Recorded lesson", media = listOf(first, second), videoId = "CMiPYHNNg28", videoPositionMillis = 92000)
        app.repository.save(note, "<p>Keep this text</p>")
        var source: Pair<String, Long>? = null
        val restoration = StateRestorationTester(compose)
        restoration.setContent { GardenTheme { NoteEditor(note.id, note, app.repository, {}, { updated, html -> app.repository.save(updated, html) }, {}, onVideo = { id, at -> source = id to at }) } }
        compose.onNodeWithText("Watch from 1:32").performScrollTo().performClick()
        assertEquals("CMiPYHNNg28" to 92000L, source)
        compose.onNodeWithContentDescription("Remove audio 1").performScrollTo().performClick()
        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithTag("note-audio-$first").assertDoesNotExist()
        compose.onNodeWithTag("note-audio-$second").assertExists()
        compose.onNodeWithText("Save").performClick()
        compose.waitUntil(5000) { app.repository.state.value.notes.single().media == listOf(second) }
        assertTrue(app.repository.body(note.id).contains("Keep this text"))
        assertEquals(92000L, app.repository.state.value.notes.single().videoPositionMillis)
    }
}
