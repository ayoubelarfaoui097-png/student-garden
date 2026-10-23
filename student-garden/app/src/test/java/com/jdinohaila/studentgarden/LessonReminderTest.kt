package com.jdinohaila.studentgarden

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationManager
import androidx.test.core.app.ApplicationProvider
import com.jdinohaila.studentgarden.domain.Lesson
import com.jdinohaila.studentgarden.platform.LessonReminder
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = GardenApp::class)
class LessonReminderTest {
    private lateinit var app: GardenApp
    private val now = 1_800_000_000_000L

    @Before fun setup() {
        app = ApplicationProvider.getApplicationContext()
        app.getSharedPreferences("settings", 0).edit().clear().putBoolean("lessonReminders", true).putInt("dailyReminderMinute", java.time.Instant.ofEpochMilli(now).atZone(java.time.ZoneId.systemDefault()).let { it.hour * 60 + it.minute }).commit()
        shadowOf(app).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
    }

    @Test fun schedulesOnlyTheNextUnannouncedDeadline() {
        val late = Lesson(title = "First", dueAt = now - 5000)
        val future = Lesson(title = "Later", dueAt = now + 70000)
        LessonReminder.schedule(app, listOf(late, future), now)
        val alarms = shadowOf(app.getSystemService(AlarmManager::class.java)).scheduledAlarms
        assertEquals(1, alarms.size)
        assertEquals(now + 1000, alarms.single().triggerAtTime)
        assertEquals(AlarmManager.RTC_WAKEUP, alarms.single().type)
    }

    @Test fun deliveringDoesNotRepeatAndReschedulesAfterActualReview() {
        val lesson = Lesson(title = "Private lesson", dueAt = now - 1)
        LessonReminder.deliver(app, listOf(lesson), now)
        val manager = app.getSystemService(NotificationManager::class.java)
        assertNotNull(shadowOf(manager).getNotification(LessonReminder.NOTIFICATION_ID))
        val prefs = app.getSharedPreferences("settings", 0)
        assertTrue(LessonReminder.token(lesson) in prefs.getStringSet("lessonDelivered", emptySet())!!)
        manager.cancelAll()
        LessonReminder.deliver(app, listOf(lesson), now)
        assertEquals(0, shadowOf(manager).size())
        val reviewed = lesson.copy(stage = 1, dueAt = now + 7 * 86400000L)
        LessonReminder.schedule(app, listOf(reviewed), now)
        assertTrue(prefs.getStringSet("lessonDelivered", emptySet())!!.isEmpty())
        assertEquals(com.jdinohaila.studentgarden.platform.ReminderTiming.studyTime(reviewed.dueAt!!, now, app.getSharedPreferences("settings", 0).getInt("dailyReminderMinute", 540), java.time.ZoneId.systemDefault()), shadowOf(app.getSystemService(AlarmManager::class.java)).scheduledAlarms.single().triggerAtTime)
    }

    @Test fun deniedPermissionLeavesTheDueLessonEligibleForLater() {
        val lesson = Lesson(title = "Biology", dueAt = now - 1)
        shadowOf(app).denyPermissions(Manifest.permission.POST_NOTIFICATIONS)
        LessonReminder.deliver(app, listOf(lesson), now)
        assertEquals(0, shadowOf(app.getSystemService(NotificationManager::class.java)).size())
        assertTrue(app.getSharedPreferences("settings", 0).getStringSet("lessonDelivered", emptySet())!!.isEmpty())
        assertTrue(shadowOf(app.getSystemService(AlarmManager::class.java)).scheduledAlarms.isEmpty())
    }

    @Test fun disablingOrCompletingCancelsPendingAlarms() {
        val lesson = Lesson(title = "Biology", dueAt = now + 50000)
        LessonReminder.schedule(app, listOf(lesson), now)
        app.getSharedPreferences("settings", 0).edit().putBoolean("lessonReminders", false).commit()
        LessonReminder.schedule(app, listOf(lesson), now)
        assertTrue(shadowOf(app.getSystemService(AlarmManager::class.java)).scheduledAlarms.isEmpty())
        assertTrue(LessonReminder.pending(listOf(lesson.copy(stage = 3, dueAt = null)), emptySet()).isEmpty())
    }
}
