@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
package com.jdinohaila.studentgarden

import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import com.jdinohaila.studentgarden.data.GardenRepository
import com.jdinohaila.studentgarden.video.*
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.CompletableDeferred
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.*
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = GardenApp::class)
class VideoPlaybackPolicyTest {
    @Test fun startupPrefersAdaptiveOrDirect720AndFallbackNeverSelectsAHigherQuality() {
        val tracks = listOf(VideoTrack("2160p", "https://example.test/4k"), VideoTrack("720p", "https://example.test/720"),
            VideoTrack("360p", "https://example.test/360"), VideoTrack("144p", "https://example.test/144"))
        assertEquals(1, VideoPlaybackPolicy.initialTrack(tracks))
        assertEquals(2, VideoPlaybackPolicy.fallbackTrack(tracks, 1))
        assertNull(VideoPlaybackPolicy.fallbackTrack(tracks, 3))
        val auto = listOf(VideoTrack("Auto", "https://example.test/hls", hls = true)) + tracks
        assertEquals(0, VideoPlaybackPolicy.initialTrack(auto)); assertEquals(3, VideoPlaybackPolicy.fallbackTrack(auto, 0))
        assertEquals(1, VideoPlaybackPolicy.fallbackTrack(listOf(auto[0], tracks[1]), 0))
    }
    @Test fun qualityChangeReusesThePlayerAndRetainsPausedStateAndPosition() = runBlocking {
        val app = ApplicationProvider.getApplicationContext<GardenApp>()
        app.deleteDatabase("garden.db"); val repo = GardenRepository(app); repo.load()
        val tracks = listOf(VideoTrack("720p", "https://example.test/720.mp4"), VideoTrack("360p", "https://example.test/360.mp4"))
        val catalog = object : VideoCatalog {
            override suspend fun search(query: String) = emptyList<VideoItem>()
            override suspend fun lesson(videoId: String) = VideoLesson(VideoItem(videoId, "Biology", "Teacher", 300), tracks)
        }
        val model = VideoPlayerModel(app, repo, catalog, "CMiPYHNNg28", 42000)
        model.open()
        val until = System.currentTimeMillis() + 5000
        while (model.player == null && System.currentTimeMillis() < until) {
            Shadows.shadowOf(Looper.getMainLooper()).idle(); Thread.sleep(10)
        }
        val original = model.player!!
        original.pause(); model.seekTo(78000)
        model.selectTrack(1)
        assertSame(original, model.player)
        assertFalse(model.player!!.playWhenReady); assertEquals(78000L, model.player!!.currentPosition)
        model.selectTrack(999); assertSame(original, model.player); assertEquals(1, model.trackIndex)
        model.close(); model.open()
        val reopened = System.currentTimeMillis() + 5000
        while (model.player == null && System.currentTimeMillis() < reopened) {
            Shadows.shadowOf(Looper.getMainLooper()).idle(); Thread.sleep(10)
        }
        assertEquals("Reopening the source must honor its timestamp", 42000L, model.player!!.currentPosition)
        model.close()
        Shadows.shadowOf(Looper.getMainLooper()).idle(); Thread.sleep(40)
        repo.database.close()
        Unit
    }
    @Test fun replacingOrClosingAnExtractionCannotResetTheNewLoadingStateOrRecreateAPlayer() = runBlocking {
        val app = ApplicationProvider.getApplicationContext<GardenApp>()
        app.deleteDatabase("garden.db"); val repo = GardenRepository(app); repo.load()
        val gate = CompletableDeferred<VideoLesson>()
        var loads = 0
        val catalog = object : VideoCatalog {
            override suspend fun search(query: String) = emptyList<VideoItem>()
            override suspend fun lesson(videoId: String): VideoLesson { loads++; return gate.await() }
        }
        val model = VideoPlayerModel(app, repo, catalog, "CMiPYHNNg28")
        model.open(); Shadows.shadowOf(Looper.getMainLooper()).idle()
        model.reload(); Shadows.shadowOf(Looper.getMainLooper()).idle()
        assertEquals(2, loads); assertTrue(model.loading)
        model.close(); gate.complete(VideoLesson(VideoItem("CMiPYHNNg28", "Lesson", "Teacher", 300), emptyList()))
        Shadows.shadowOf(Looper.getMainLooper()).idle()
        assertFalse(model.loading); assertNull(model.player)
        Thread.sleep(40); repo.database.close()
        Unit
    }

    @Test fun openingWithoutASourceTimestampRetainsTheLastPlaybackPosition() = runBlocking {
        val app = ApplicationProvider.getApplicationContext<GardenApp>()
        app.deleteDatabase("garden.db"); val repo = GardenRepository(app); repo.load()
        val catalog = object : VideoCatalog {
            override suspend fun search(query: String) = emptyList<VideoItem>()
            override suspend fun lesson(videoId: String) = VideoLesson(VideoItem(videoId, "Lesson", "Teacher", 300),
                listOf(VideoTrack("360p", "https://example.test/360.mp4")))
        }
        val model = VideoPlayerModel(app, repo, catalog, "CMiPYHNNg28")
        fun ready() {
            val until = System.currentTimeMillis() + 5000
            while (model.player == null && System.currentTimeMillis() < until) {
                Shadows.shadowOf(Looper.getMainLooper()).idle(); Thread.sleep(10)
            }
        }
        model.open(); ready(); model.seekTo(78000); model.close()
        model.open(); ready()
        assertEquals(78000L, model.player!!.currentPosition)
        model.close(); Shadows.shadowOf(Looper.getMainLooper()).idle(); Thread.sleep(40)
        repo.database.close()
        Unit
    }

}
