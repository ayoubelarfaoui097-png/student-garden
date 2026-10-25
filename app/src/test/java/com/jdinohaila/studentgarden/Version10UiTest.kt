package com.jdinohaila.studentgarden

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.core.app.ApplicationProvider
import com.jdinohaila.studentgarden.ui.GardenRoot
import com.jdinohaila.studentgarden.ui.theme.GardenTheme
import com.jdinohaila.studentgarden.video.*
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.flow
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.*
import org.robolectric.annotation.*

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w412dp-h915dp-xxhdpi", application = GardenApp::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class Version10UiTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private lateinit var app: GardenApp
    private var moreCalls = 0
    private var channelCalls = 0
    private val first = VideoItem("CMiPYHNNg28", "Biology lesson", "Teacher", 300)
    @Before fun setup() = runBlocking {
        app = ApplicationProvider.getApplicationContext(); app.deleteDatabase("garden.db"); app.repository.load()
        app.videoCatalog = object : VideoCatalog {
            override suspend fun search(query: String) = listOf(first)
            override suspend fun searchPage(query: String, filters: SearchFilters, next: CatalogCursor?): VideoSearchPage {
                if (filters.type == SearchType.CHANNELS) return VideoSearchPage(listOf(ChannelHit(ChannelItem("https://www.youtube.com/@teacher", "Teacher"))))
                if (next != null) { moreCalls++; return VideoSearchPage(listOf(VideoHit(VideoItem("dQw4w9WgXcQ", "Next lesson", "Teacher", 600)))) }
                return VideoSearchPage(listOf(VideoHit(first)), object : CatalogCursor {})
            }
            override suspend fun channel(url: String, next: CatalogCursor?): VideoChannel {
                channelCalls++; return VideoChannel(ChannelItem(url, "Teacher"), listOf(first))
            }
            override suspend fun lesson(videoId: String) = VideoLesson(first, emptyList())
        }
        compose.setContent { GardenTheme { GardenRoot(app.repository, app.media, {}) } }
    }
    @Test fun reachingTheEndLoadsResultsWithoutALoadMoreButton() {
        compose.onNodeWithTag("nav-1").performClick()
        compose.onNodeWithTag("video-query").performTextInput("biology")
        compose.onNodeWithContentDescription("Search or open video").performClick()
        compose.waitUntil(5000) { moreCalls == 1 }
        compose.onNodeWithText("Next lesson").assertExists()
        compose.onNodeWithText("Load more").assertDoesNotExist()
    }
    @Test fun videoResultsAppearWhilePlaylistAndChannelSuggestionsAreStillLoading() {
        val suggestions = CompletableDeferred<Unit>()
        app.videoCatalog = object : VideoCatalog {
            override suspend fun search(query: String) = listOf(first)
            override suspend fun lesson(videoId: String): VideoLesson = error("unused")
            override fun searchUpdates(query: String, filters: SearchFilters) = flow {
                emit(VideoSearchPage(listOf(VideoHit(first))))
                suggestions.await()
                emit(VideoSearchPage(listOf(VideoHit(first), PlaylistHit(PlaylistItem("PL12345678901234567890123456789012", "Biology course", "Teacher", 12)))))
            }
        }
        compose.onNodeWithTag("nav-1").performClick()
        compose.onNodeWithTag("video-query").performTextInput("biology")
        compose.onNodeWithContentDescription("Search or open video").performClick()
        compose.onNodeWithText(first.title).assertExists()
        compose.onNodeWithText("Biology course").assertDoesNotExist()
        compose.runOnIdle { suggestions.complete(Unit) }
        compose.onNodeWithText("Biology course").assertExists()
    }
    @Test fun channelFilterOpensAChannelAndItsVideos() {
        compose.onNodeWithTag("nav-1").performClick()
        compose.onNodeWithTag("video-query").performTextInput("teacher")
        compose.onNodeWithContentDescription("Search or open video").performClick()
        compose.onNodeWithText("Filters").performClick()
        compose.onNodeWithText("All").performClick()
        compose.onNodeWithText("Channels").performClick()
        compose.onNodeWithText("Apply").performClick()
        compose.onNode(hasText("Teacher") and hasClickAction()).performScrollTo().performClick()
        compose.waitUntil(5000) { channelCalls == 1 }
        compose.onNodeWithText("Biology lesson").assertExists()
    }
    @Test fun videoNotesOfferOnlyTextAndFramesAndSaveToTheVideoNotebook() {
        compose.onNodeWithTag("nav-1").performClick()
        compose.onNodeWithTag("video-query").performTextInput(VideoLinks.url(first.videoId))
        compose.onNodeWithContentDescription("Search or open video").performClick()
        compose.onNodeWithText("Record audio").assertDoesNotExist()
        compose.onNodeWithText("Take a note").performScrollTo().performClick()
        compose.onNodeWithTag("video-note-text").performTextInput("A focused observation")
        compose.onNodeWithText("Save").performScrollTo().performClick()
        compose.waitUntil(5000) { app.repository.state.value.notes.isNotEmpty() }
        assertEquals(first.videoId, app.repository.state.value.notes.single().videoId)
    }
}
