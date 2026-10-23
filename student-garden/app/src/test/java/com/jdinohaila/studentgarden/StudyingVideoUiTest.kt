package com.jdinohaila.studentgarden

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.core.app.ApplicationProvider
import com.jdinohaila.studentgarden.domain.Lesson
import com.jdinohaila.studentgarden.ui.GardenRoot
import com.jdinohaila.studentgarden.ui.theme.GardenTheme
import com.jdinohaila.studentgarden.video.*
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w412dp-h915dp-xxhdpi", application = GardenApp::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class StudyingVideoUiTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private lateinit var app: GardenApp
    @Before fun setup() = runBlocking {
        app = ApplicationProvider.getApplicationContext()
        app.deleteDatabase("garden.db"); app.repository.load()
        app.videoCatalog = object : VideoCatalog {
            override suspend fun search(query: String) = listOf(VideoItem("dQw4w9WgXcQ", "Cell division explained", "Teacher", 600))
            override suspend fun lesson(videoId: String): VideoLesson = throw java.io.IOException("offline fixture")
        }
        compose.setContent { GardenTheme { GardenRoot(app.repository, app.media, {}) } }
    }
    @Test fun studyContainsFourSectionsAndTasksRemainOnToday() {
        compose.onNodeWithTag("nav-2").performClick()
        listOf("Agenda", "Notes", "Courses", "Lessons").forEach { compose.onNodeWithText(it).assertExists() }
        compose.onNodeWithText("Tasks").assertDoesNotExist()
        compose.onNodeWithTag("nav-0").performClick()
        compose.onNodeWithText("Add your next assignment or study task.").assertExists()
        compose.onNodeWithTag("nav-1").performClick()
        compose.onNodeWithTag("video-query").performTextInput("biology")
        compose.onNodeWithContentDescription("Search or open video").performClick()
        compose.onNodeWithText("Cell division explained").assertIsDisplayed()
    }
    @Test fun customLessonColorSavesWithoutChangingItsReviewDeadline() = runBlocking {
        val lesson = Lesson(title = "Cells", stage = 1, dueAt = 123456789)
        app.repository.save(lesson)
        compose.onNodeWithTag("nav-2").performClick()
        compose.onNodeWithText("Lessons").performScrollTo().performClick()
        compose.onNodeWithText("Cells").performClick()
        compose.onNodeWithText("Custom color").performScrollTo().performTextInput("#12abef")
        compose.onNodeWithText("Save").performClick()
        compose.waitUntil(5000) { app.repository.state.value.lessons.single().color == "#12ABEF" }
        assertEquals(lesson.dueAt, app.repository.state.value.lessons.single().dueAt)
    }
    @Test @Config(qualifiers = "w800dp-h360dp-land-xhdpi")
    fun landscapePlayerRemovesTheBottomNavigationAndWideNavigationRailEvenOnError() {
        compose.onNodeWithTag("nav-1").performClick()
        compose.onNodeWithTag("video-query").performTextInput("https://youtu.be/dQw4w9WgXcQ")
        compose.onNodeWithContentDescription("Search or open video").performClick()
        compose.onNodeWithTag("video-fullscreen").assertIsDisplayed()
        (0..4).forEach { compose.onNodeWithTag("nav-$it").assertDoesNotExist() }
    }
}
