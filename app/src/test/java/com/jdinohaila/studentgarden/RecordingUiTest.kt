package com.jdinohaila.studentgarden

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.jdinohaila.studentgarden.ui.RecordingStatus
import com.jdinohaila.studentgarden.ui.theme.GardenTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w320dp-h720dp-hdpi", application = GardenApp::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class RecordingUiTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    @Test fun recordingShowsElapsedTimeAndAWorkingStopControlOnSmallPhones() {
        var stopped = 0
        compose.setContent { GardenTheme { RecordingStatus(65_000) { stopped++ } } }
        compose.onNodeWithText("Recording…").assertIsDisplayed()
        compose.onNodeWithText("01:05 / 03:00").assertIsDisplayed()
        compose.onNodeWithText("Stop recording").performClick()
        assertEquals(1, stopped)
    }
}
