package com.jdinohaila.studentgarden

import android.graphics.Bitmap
import android.os.Looper
import android.view.SurfaceView
import androidx.test.core.app.ApplicationProvider
import com.jdinohaila.studentgarden.data.GardenRepository
import com.jdinohaila.studentgarden.video.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35],application=GardenApp::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class CapturePlaybackTest {
    @Test fun capturingFramesKeepsPlayingVideosPlayingAndPausedVideosPaused()=runBlocking {
        val app=ApplicationProvider.getApplicationContext<GardenApp>()
        app.deleteDatabase("garden.db"); val repo=GardenRepository(app);repo.load()
        val previous=app.frameCapture
        app.frameCapture=object:VideoFrameCapture {
            override suspend fun capture(surface:SurfaceView)=Bitmap.createBitmap(160,90,Bitmap.Config.ARGB_8888)
        }
        val catalog=object:VideoCatalog {
            override suspend fun search(query:String)=emptyList<VideoItem>()
            override suspend fun lesson(videoId:String)=VideoLesson(VideoItem(videoId,"Lesson","Teacher",300),listOf(VideoTrack("360p","https://example.test/video.mp4")))
        }
        val model=VideoPlayerModel(app,repo,catalog,"CMiPYHNNg28")
        fun waitFor(condition:()->Boolean) {
            val until=System.currentTimeMillis()+5000
            while(!condition() && System.currentTimeMillis()<until){Shadows.shadowOf(Looper.getMainLooper()).idle();Thread.sleep(10)}
            assertTrue(condition())
        }
        try {
            model.open();waitFor { model.player!=null }
            val player=model.player!!
            player.playWhenReady=true
            model.captureFrame(SurfaceView(app));assertTrue(player.playWhenReady)
            waitFor { !model.capturing && repo.state.value.notes.isNotEmpty() }
            player.pause()
            model.captureFrame(SurfaceView(app));assertFalse(player.playWhenReady)
            waitFor { !model.capturing }
        } finally {
            model.close();Shadows.shadowOf(Looper.getMainLooper()).idle();Thread.sleep(40)
            app.frameCapture=previous;repo.database.close()
        }
        Unit
    }
}
