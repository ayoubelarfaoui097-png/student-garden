package com.jdinohaila.studentgarden

import android.graphics.*
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.core.app.ApplicationProvider
import com.jdinohaila.studentgarden.domain.*
import com.jdinohaila.studentgarden.ui.*
import com.jdinohaila.studentgarden.ui.screens.*
import com.jdinohaila.studentgarden.ui.theme.GardenTheme
import kotlinx.coroutines.runBlocking
import java.io.File
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.*

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35],qualifiers="w412dp-h915dp-xxhdpi",application=GardenApp::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class Notebook12UiTest {
    @get:Rule val compose=createAndroidComposeRule<ComponentActivity>()
    private lateinit var app: GardenApp
    @Before fun setup()=runBlocking {
        app=ApplicationProvider.getApplicationContext(); app.deleteDatabase("garden.db"); app.repository.load()
        app.repository.save(Note(title="Course notes"),"<p>Ordinary study note</p>")
        app.repository.appendVideoNote(Note(title="Mitosis explained",videoId="CMiPYHNNg28",videoPositionMillis=15000),"<p>DNA is copied before division.</p>")
    }
    private fun screenshot(name:String) { compose.runOnIdle {
        val view=compose.activity.window.decorView
        val image=Bitmap.createBitmap(view.width,view.height,Bitmap.Config.ARGB_8888)
        view.draw(Canvas(image)); val file=File("build/qa/$name.png"); file.parentFile!!.mkdirs()
        file.outputStream().use { image.compress(Bitmap.CompressFormat.PNG,100,it) }; image.recycle()
    } }
    @Test fun videoNotebooksStayInVideoAndTheirEntriesCanBeEditedAndExported() {
        compose.setContent { GardenTheme { GardenRoot(app.repository,app.media,{}) } }
        compose.onNodeWithTag("nav-2").performClick()
        compose.onNodeWithText("Course notes").assertExists(); compose.onNodeWithText("Mitosis explained").assertDoesNotExist()
        compose.onNodeWithTag("nav-1").performClick(); compose.onNodeWithText("Notebooks").performClick()
        compose.onNodeWithText("Mitosis explained").performClick()
        compose.onNodeWithTag("video-notebook-page").assertExists()
        compose.onNodeWithText("DNA is copied before division.").assertExists()
        compose.onNodeWithContentDescription("Edit").performClick()
        compose.onNodeWithTag("edit-video-entry").performTextReplacement("Chromosomes separate during anaphase.")
        compose.onNodeWithText("Save").performClick()
        compose.waitUntil(5000) {
            compose.onAllNodesWithTag("edit-video-entry").fetchSemanticsNodes().isEmpty() &&
                compose.onAllNodesWithText("Chromosomes separate during anaphase.").fetchSemanticsNodes().size == 1
        }
        compose.onNodeWithText("Chromosomes separate during anaphase.").assertExists()
        compose.onNodeWithContentDescription("Export note").performClick()
        listOf("PDF","Word (.docx)","Slides (.pptx)").forEach { compose.onNodeWithText(it).assertExists() }
    }
    @Test fun frameControlsAreSeparateFromTheImageAndTextAndFramesReadTogether()=runBlocking {
        val id="${newId()}.jpg"
        val bitmap=Bitmap.createBitmap(960,540,Bitmap.Config.ARGB_8888).apply { eraseColor(Color.rgb(220,237,250)) }
        Canvas(bitmap).drawText("Chromosomes separate",60f,250f,Paint(Paint.ANTI_ALIAS_FLAG).apply { color=Color.rgb(0,82,204); textSize=50f })
        app.media.file(id).outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG,95,it) }; bitmap.recycle()
        app.repository.appendVideoNote(Note(title="Mitosis explained",videoId="CMiPYHNNg28",videoPositionMillis=31000,images=listOf(id)))
        val note=app.repository.state.value.notes.first { it.videoId!=null }
        compose.setContent { GardenTheme { VideoNotebookScreen(note.id,app.repository,app.media,{},{},{_,_->}) } }
        compose.onNodeWithTag("frame-remove").assertExists(); compose.onNodeWithTag("frame-time").assertExists()
        screenshot("notebook-012-phone")
        compose.onNodeWithContentDescription("Remove frame").performClick()
        compose.waitUntil(5000) { app.repository.state.value.notes.first { it.videoId!=null }.images.isEmpty() }
        compose.onNodeWithText("Undo").performClick()
        compose.waitUntil(5000) { app.repository.state.value.notes.first { it.videoId!=null }.images.isNotEmpty() }
        Unit
    }
    @Test @Config(qualifiers="w800dp-h360dp-land-xhdpi")
    fun fullscreenCaptureFollowsControllerVisibilityAndKeepsTheVideoSurface() {
        var visible by mutableStateOf(true); var captures=0
        compose.setContent { GardenTheme { VideoPlayerLayout(true,"Lesson",{},
            playerContent={ Box(Modifier.fillMaxSize().testTag("surface")) },
            showFullscreenControls=visible,fullscreenActions={ IconButton(onClick={captures++}) { Icon(Icons.Outlined.PhotoCamera,"Capture frame") } }) } }
        compose.onNodeWithContentDescription("Capture frame").performClick(); assertEquals(1,captures)
        screenshot("fullscreen-capture-012")
        compose.runOnIdle { visible=false }
        compose.onNodeWithContentDescription("Capture frame").assertDoesNotExist()
        compose.onNodeWithTag("surface").assertExists()
    }
}
