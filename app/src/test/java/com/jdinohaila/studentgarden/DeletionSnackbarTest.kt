package com.jdinohaila.studentgarden

import androidx.compose.material3.SnackbarHostState
import com.jdinohaila.studentgarden.ui.showDeletionSnackbar
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = GardenApp::class)
class DeletionSnackbarTest {
    @Test fun deletionPromptDismissesAfterThreeSeconds() = runBlocking {
        val host = SnackbarHostState()
        val started = System.nanoTime()
        val result = async(start = CoroutineStart.UNDISPATCHED) { showDeletionSnackbar(host, "Moved to trash", "Undo") }
        assertNotNull(host.currentSnackbarData)
        delay(500)
        assertNotNull(host.currentSnackbarData)
        assertFalse(withTimeout(6_000) { result.await() })
        assertNull(host.currentSnackbarData)
        assertTrue((System.nanoTime() - started) / 1_000_000 >= 2_900)
    }

    @Test fun undoDuringThePromptReturnsTheActionImmediately() = runBlocking {
        val host = SnackbarHostState()
        val result = async(start = CoroutineStart.UNDISPATCHED) { showDeletionSnackbar(host, "Moved to trash", "Undo") }
        host.currentSnackbarData!!.performAction()
        assertTrue(withTimeout(1_000) { result.await() })
        assertNull(host.currentSnackbarData)
    }
}
