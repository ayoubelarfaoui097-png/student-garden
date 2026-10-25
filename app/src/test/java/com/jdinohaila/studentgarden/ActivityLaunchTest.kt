package com.jdinohaila.studentgarden

import android.content.Intent
import android.os.Bundle
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = GardenApp::class)
class ActivityLaunchTest {
    private fun intent(token: Long) = Intent(ApplicationProvider.getApplicationContext<GardenApp>(), MainActivity::class.java)
        .putExtra("lesson_request_token", token)

    @Test fun newReminderDeliveredToExistingActivityIsHandledOnce() {
        val controller = Robolectric.buildActivity(MainActivity::class.java).setup()
        try {
            controller.newIntent(intent(100))
            assertEquals(1, controller.get().lessonRequest)
            controller.newIntent(intent(100))
            assertEquals(1, controller.get().lessonRequest)
            controller.newIntent(intent(200))
            assertEquals(2, controller.get().lessonRequest)
        } finally { controller.pause().stop().destroy() }
    }

    @Test fun coldLaunchProcessesANewReminderEvenWhenAndroidRestoresOldState() {
        val saved = Bundle().apply { putInt("lessonRequest", 3); putLong("lessonToken", 100) }
        val controller = Robolectric.buildActivity(MainActivity::class.java, intent(200)).create(saved).start().resume()
        try { assertEquals(4, controller.get().lessonRequest) }
        finally { controller.pause().stop().destroy() }
    }

    @Test fun categoryAndFocusIntentsWorkOnColdAndWarmLaunches() {
        val app = ApplicationProvider.getApplicationContext<GardenApp>()
        val controller = Robolectric.buildActivity(MainActivity::class.java, com.jdinohaila.studentgarden.platform.StudyReminders.openIntent(app, "tasks", 100)).setup()
        try {
            assertEquals("tasks", controller.get().reminderDestination)
            assertEquals(1, controller.get().reminderRequest)
            listOf("agenda", "cards", "habits", "focus", "settings").forEachIndexed { i, destination ->
                val intent = com.jdinohaila.studentgarden.platform.StudyReminders.openIntent(app, destination, 200L + i)
                controller.newIntent(intent)
                controller.newIntent(intent)
                assertEquals(destination, controller.get().reminderDestination)
                assertEquals(i + 2, controller.get().reminderRequest)
            }
            controller.newIntent(com.jdinohaila.studentgarden.platform.StudyReminders.openIntent(app, "invalid", 900))
            assertEquals(6, controller.get().reminderRequest)
        } finally { controller.pause().stop().destroy() }
    }

    @Test fun configurationRecreationDoesNotReopenAnAlreadyHandledReminder() {
        val saved = Bundle().apply { putInt("lessonRequest", 3); putLong("lessonToken", 100) }
        val controller = Robolectric.buildActivity(MainActivity::class.java, intent(100)).create(saved).start().resume()
        try { assertEquals(3, controller.get().lessonRequest) }
        finally { controller.pause().stop().destroy() }
    }
}
