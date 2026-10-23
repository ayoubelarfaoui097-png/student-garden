package com.jdinohaila.studentgarden

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Looper
import android.view.SurfaceView
import androidx.test.core.app.ApplicationProvider
import com.jdinohaila.studentgarden.data.GardenRepository
import com.jdinohaila.studentgarden.video.*
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.*
import org.robolectric.annotation.*

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = GardenApp::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class FrameCaptureTest {
    private lateinit var app: GardenApp
    private lateinit var repo: GardenRepository
    private lateinit var previous: VideoFrameCapture
    private val catalog = object : VideoCatalog {
        override suspend fun search(query: String) = emptyList<VideoItem>()
        override suspend fun lesson(videoId: String): VideoLesson = error("Not used")
    }
    @Before fun setup() = runBlocking {
        app = ApplicationProvider.getApplicationContext(); previous = app.frameCapture
        app.deleteDatabase("garden.db"); repo = GardenRepository(app); repo.load()
    }
    @After fun close() { app.frameCapture = previous; repo.database.close() }
    private fun waitFor(condition: () -> Boolean) {
        val until = System.currentTimeMillis() + 5000
        while (!condition() && System.currentTimeMillis() < until) { Shadows.shadowOf(Looper.getMainLooper()).idle(); Thread.sleep(10) }
        assertTrue(condition())
    }
    @Test fun capturedSurfaceSavesAnImageNoteWithTheCurrentVideoAndTimestamp() {
        app.frameCapture = object : VideoFrameCapture {
            override suspend fun capture(surface: SurfaceView) = Bitmap.createBitmap(160, 90, Bitmap.Config.ARGB_8888).apply { eraseColor(android.graphics.Color.GREEN) }
        }
        val model = VideoPlayerModel(app, repo, catalog, "CMiPYHNNg28", 42000)
        model.captureFrame(SurfaceView(app))
        waitFor { repo.state.value.notes.isNotEmpty() && model.noteMessage == R.string.video_note_saved }
        val note = repo.state.value.notes.single()
        assertEquals("CMiPYHNNg28", note.videoId); assertEquals(42000L, note.videoPositionMillis)
        val bitmap = BitmapFactory.decodeFile(app.media.file(note.images.single()).absolutePath)
        assertEquals(160, bitmap.width); assertEquals(90, bitmap.height); bitmap.recycle()
        assertEquals(R.string.video_note_saved, model.noteMessage)
    }
    @Test fun failedSurfaceCopyNeverAddsABlankImageNote() {
        app.frameCapture = object : VideoFrameCapture { override suspend fun capture(surface: SurfaceView): Bitmap = throw IllegalStateException("No decoded frame") }
        val model = VideoPlayerModel(app, repo, catalog, "CMiPYHNNg28")
        model.captureFrame(SurfaceView(app))
        waitFor { !model.capturing }
        assertTrue(repo.state.value.notes.isEmpty()); assertEquals(R.string.frame_capture_error, model.noteMessage)
    }
}
