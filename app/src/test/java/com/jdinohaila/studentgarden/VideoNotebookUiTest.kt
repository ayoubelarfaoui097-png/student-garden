package com.jdinohaila.studentgarden

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.jdinohaila.studentgarden.domain.*
import com.jdinohaila.studentgarden.ui.screens.*
import com.jdinohaila.studentgarden.ui.theme.GardenTheme
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.*

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w412dp-h915dp-xxhdpi", application = GardenApp::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class VideoNotebookUiTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    @Test fun severalVideoEntriesDisplayOneNotebookWithSeekableTimestamps() = runBlocking {
        val app = ApplicationProvider.getApplicationContext<GardenApp>()
        app.deleteDatabase("garden.db"); app.repository.load()
        app.repository.appendVideoNote(Note(title = "Biology", preview = "Cells", videoId = "CMiPYHNNg28", videoPositionMillis = 42000), "<p>Cells</p>")
        app.repository.appendVideoNote(Note(title = "Biology", preview = "Water", videoId = "CMiPYHNNg28", videoPositionMillis = 83000), "<p>Water</p>")
        var seek = -1L; var edited = ""
        compose.setContent { GardenTheme {
            val state by app.repository.state.collectAsState()
            Column { VideoNotes(remember { VideoNoteDraft() }, "CMiPYHNNg28", "Biology", state.notes, app.repository, { 0 }, { seek = it }, { edited = it }) }
        } }
        compose.onAllNodesWithTag("video-notebook").assertCountEquals(1)
        compose.onNodeWithText("0:42", substring = false).performClick(); assertEquals(42000L, seek)
        compose.onNodeWithText("1:23", substring = false).performClick(); assertEquals(83000L, seek)
        compose.onNodeWithText("Edit").performClick(); assertEquals(app.repository.state.value.notes.single().id, edited)
        Unit
    }
    @Test fun videoSurfaceRemainsVisibleWhileReadingTheNotesBelowIt() {
        compose.setContent { GardenTheme {
            VideoPlayerLayout(false, "Biology", {}, playerContent = { Box(Modifier.fillMaxSize().testTag("persistent-surface")) }, details = {
                Spacer(Modifier.height(1100.dp)); Text("Last note")
            })
        } }
        compose.onNodeWithText("Last note").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("persistent-surface").assertIsDisplayed()
    }
}
