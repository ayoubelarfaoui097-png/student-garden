package com.jdinohaila.studentgarden

import android.content.pm.ActivityInfo
import androidx.activity.ComponentActivity
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.foundation.layout.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.test.core.app.ApplicationProvider
import com.jdinohaila.studentgarden.domain.*
import com.jdinohaila.studentgarden.ui.GardenRoot
import com.jdinohaila.studentgarden.ui.screens.*
import com.jdinohaila.studentgarden.ui.theme.GardenTheme
import com.jdinohaila.studentgarden.video.*
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.*

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w412dp-h915dp-xxhdpi", application = GardenApp::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class VideoFeaturesUiTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private lateinit var app: GardenApp
    private val listId = "PL12345678901234567890123456789012"
    private val first = VideoItem("CMiPYHNNg28", "First biology lesson", "Teacher", 300)
    private val second = VideoItem("dQw4w9WgXcQ", "Second biology lesson", "Teacher", 400)
    private var lastFilters = SearchFilters()
    @Before fun setup() = runBlocking {
        app = ApplicationProvider.getApplicationContext()
        app.deleteDatabase("garden.db"); app.repository.load()
        app.videoCatalog = object : VideoCatalog {
            override suspend fun search(query: String) = listOf(first, second)
            override suspend fun searchPage(query: String, filters: SearchFilters, next: CatalogCursor?): VideoSearchPage {
                lastFilters = filters
                return VideoSearchPage(if (filters.type == SearchType.PLAYLISTS) listOf(PlaylistHit(PlaylistItem(listId, "Biology course", "Teacher", 2)))
                    else listOf(VideoHit(first), VideoHit(second), PlaylistHit(PlaylistItem(listId, "Biology course", "Teacher", 2))))
            }
            override suspend fun playlist(playlistId: String, next: CatalogCursor?) = VideoPlaylist("Biology course", listOf(first, second))
            // No network or decoder needed to exercise details and navigation.
            override suspend fun lesson(videoId: String) = VideoLesson(if (videoId == first.videoId) first else second, emptyList())
        }
    }
    private fun root() { compose.setContent { GardenTheme { GardenRoot(app.repository, app.media, {}) } }; compose.onNodeWithTag("nav-1").performClick() }
    @Test fun relevanceOrderIsPreservedAndPlaylistFilterReachesTheCatalog() {
        root()
        compose.onNodeWithTag("video-query").performTextInput("biology")
        compose.onNodeWithContentDescription("Search or open video").performClick()
        val firstY = compose.onNodeWithText(first.title).fetchSemanticsNode().positionInRoot.y
        val secondY = compose.onNodeWithText(second.title).fetchSemanticsNode().positionInRoot.y
        assertTrue(firstY < secondY)
        assertEquals(SearchSort.RELEVANCE, lastFilters.sort)
        compose.onNodeWithText("Filters").performClick()
        compose.onNodeWithText("All").performClick()
        compose.onNodeWithText("Playlists").performClick()
        compose.onNodeWithText("Apply").performClick()
        compose.onNodeWithText("Biology course").assertExists()
        compose.onNodeWithText(first.title).assertDoesNotExist()
        assertEquals(SearchType.PLAYLISTS, lastFilters.type)
    }
    @Test fun pastedPlaylistOpensItsLessonsAndKeepsOtherLessonsAvailableWhileWatching() {
        root()
        compose.onNodeWithTag("video-query").performTextInput(PlaylistLinks.url(listId))
        compose.onNodeWithContentDescription("Search or open video").performClick()
        compose.onNodeWithText(first.title).performClick()
        compose.onNodeWithText("Biology course").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText(second.title).performScrollTo().performClick()
        compose.onAllNodesWithText(second.title).assertCountEquals(2)
        compose.onNodeWithText(first.title).performScrollTo().assertIsDisplayed()
    }
    @Test @Config(qualifiers = "w800dp-h360dp-land-xhdpi")
    fun fullscreenArrowMinimizesBeforeLeavingAndRestoresTheOriginalOrientation() {
        root()
        compose.onNodeWithTag("video-query").performTextInput(VideoLinks.url(first.videoId))
        compose.onNodeWithContentDescription("Search or open video").performClick()
        compose.onNodeWithTag("video-fullscreen").assertIsDisplayed()
        compose.onNodeWithContentDescription("Exit fullscreen").performClick()
        compose.onNodeWithTag("video-fullscreen").assertDoesNotExist()
        assertEquals(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT, compose.activity.requestedOrientation)
        (0..4).forEach { compose.onNodeWithTag("nav-$it").assertDoesNotExist() }
        compose.onNodeWithContentDescription("Close").performClick()
        compose.onNodeWithTag("nav-1").assertExists()
        assertEquals(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED, compose.activity.requestedOrientation)
    }
    @Test fun changingPlaylistLessonsProtectsAnUnsavedVideoNote() {
        root()
        compose.onNodeWithTag("video-query").performTextInput(PlaylistLinks.url(listId))
        compose.onNodeWithContentDescription("Search or open video").performClick()
        compose.onNodeWithText(first.title).performClick()
        compose.onNodeWithText("Take a note").performScrollTo().performClick()
        compose.onNodeWithTag("video-note-text").performTextInput("Remember the cell wall")
        compose.onNodeWithTag("video-playlist").performScrollTo()
        compose.onNodeWithText(second.title).performScrollTo().performClick()
        compose.onNodeWithText("Discard changes?").assertIsDisplayed()
        compose.onNodeWithText("Keep editing").performClick()
        compose.onNodeWithTag("video-note-text").performScrollTo().assertTextContains("Remember the cell wall")
    }
    @Test fun timestampedNoteDraftSurvivesRestorationAndSavesEscapedTextToStudying() = runBlocking {
        val restore = StateRestorationTester(compose)
        restore.setContent { GardenTheme {
            val draft = rememberSaveable(saver = VideoNoteDraft.Saver) { VideoNoteDraft() }
            val state by app.repository.state.collectAsState()
            Column(Modifier.fillMaxSize()) { VideoNotes(draft, first.videoId, first.title, state.notes, app.repository, { 42000 }, {}, {}) }
        } }
        compose.onNodeWithText("Take a note").performClick()
        compose.onNodeWithTag("video-note-text").performTextInput("Cells < plants & light")
        restore.emulateSavedInstanceStateRestore()
        compose.onNodeWithTag("video-note-text").assertTextContains("Cells < plants & light")
        compose.onNodeWithText("Watch from 0:42").assertExists()
        compose.onNodeWithText("Save").performClick()
        compose.waitUntil(5000) { app.repository.state.value.notes.isNotEmpty() }
        val note = app.repository.state.value.notes.single()
        assertEquals(first.videoId, note.videoId); assertEquals(42000L, note.videoPositionMillis)
        assertTrue(app.repository.body(note.id).contains("&lt;"))
        compose.onNodeWithText("Take a note").assertExists()
        Unit
    }
}
