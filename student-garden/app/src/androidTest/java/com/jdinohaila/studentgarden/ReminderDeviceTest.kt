package com.jdinohaila.studentgarden

import android.Manifest
import android.app.*
import android.content.*
import android.os.*
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.jdinohaila.studentgarden.domain.*
import com.jdinohaila.studentgarden.platform.*
import java.time.*
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.Assert.*
import org.junit.runner.RunWith

/** Real alarms and private receivers on the isolated emulator; no direct deliver() calls. */
@RunWith(AndroidJUnit4::class)
class ReminderDeviceTest {
    @Test fun backgroundAlarmsDeliverEveryCategoryAndDoNotRepeat() = runBlocking {
        val app = ApplicationProvider.getApplicationContext<GardenApp>()
        check(app.packageName.endsWith(".preview")) { "Use the isolated preview install only" }
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.uiAutomation.grantRuntimePermission(app.packageName, Manifest.permission.POST_NOTIFICATIONS)
        val manager = app.getSystemService(NotificationManager::class.java)
        manager.cancelAll()
        val prefs = app.getSharedPreferences("settings", 0)
        val saved = prefs.all.toMap()
        val repo = app.repository
        repo.load()
        val ids = mutableListOf<String>()
        val savedTimer = repo.state.value.timer
        val due = Instant.now().atZone(ZoneId.systemDefault()).plusMinutes(1).withSecond(0).withNano(0).let { if (it.toInstant().toEpochMilli() - System.currentTimeMillis() < 10000) it.plusMinutes(1) else it }
        val at = due.toInstant().toEpochMilli()
        try {
            prefs.edit().putBoolean("lessonReminders", true).putInt("dailyReminderMinute", due.hour * 60 + due.minute).apply()
            StudyReminders.categories.forEach { prefs.edit().putBoolean("${it.key}Reminders", true).remove("${it.key}Delivered").apply() }
            prefs.edit().remove("lessonDelivered").apply()
            val fixtures = listOf(
                Lesson(title = "QA lesson alarm", dueAt = at),
                Task(title = "QA task alarm", dueAt = at),
                StudyBlock(title = "QA agenda alarm", startsAt = at),
                Flashcard(prompt = "QA", answer = "Alarm", dueAt = at - 1000),
                Habit(title = "QA habit alarm", startDay = due.toLocalDate().toString(), zone = due.zone.id),
            )
            fixtures.forEach { ids += it.id; repo.save(it) }
            val timer = FocusTimer(minutes = 1, endsAt = at, elapsedEnd = SystemClock.elapsedRealtime() + at - System.currentTimeMillis(), boot = FocusAlarm.boot(app))
            ids += timer.id
            repo.setTimer(timer)
            FocusAlarm.schedule(app, timer)
            // Put the activity in the background without force-stopping alarms.
            app.startActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            val deadline = at + 90000
            while (System.currentTimeMillis() < deadline && !manager.activeNotifications.map { it.id }.containsAll(listOf(44,45,46,47,48,49))) kotlinx.coroutines.delay(500)
            val actual = manager.activeNotifications.map { it.id }.toSet()
            assertTrue("Actual notification IDs: $actual", actual.containsAll(listOf(44,45,46,47,48,49)))
            java.io.File(app.getExternalFilesDir(null), "reminder-device-result.txt").writeText("Delivered by real background alarms: $actual at ${Instant.now()}\n")
            val pending = manager.activeNotifications.associate { it.id to it.notification.contentIntent }
            // Taps are immutable PendingIntents supplied to the OS, rather than synthetic intents.
            listOf(44,46,47,48,49).forEach { id ->
                pending.getValue(id).send()
                kotlinx.coroutines.delay(700)
                instrumentation.runOnMainSync {
                    val activity = androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(androidx.test.runner.lifecycle.Stage.RESUMED).filterIsInstance<MainActivity>().single()
                    assertEquals(when(id) { 44 -> "focus"; 46 -> "tasks"; 47 -> "agenda"; 48 -> "cards"; else -> "habits" }, activity.reminderDestination)
                }
            }
            pending.getValue(45).send()
            kotlinx.coroutines.delay(700)
            instrumentation.runOnMainSync {
                val activity = androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(androidx.test.runner.lifecycle.Stage.RESUMED).filterIsInstance<MainActivity>().single()
                assertTrue(activity.lessonRequest > 0)
            }
            manager.cancelAll()
            repo.load()
            kotlinx.coroutines.delay(2000)
            assertTrue("Due records repeated after resuming", manager.activeNotifications.isEmpty())
        } finally {
            ids.forEach { id ->
                repo.database.writableDatabase.delete("records", "id=?", arrayOf(id))
            }
            repo.setTimer(savedTimer)
            val restore = prefs.edit().clear()
            saved.forEach { (key, value) -> when(value) {
                is Boolean -> restore.putBoolean(key,value)
                is Int -> restore.putInt(key,value)
                is Long -> restore.putLong(key,value)
                is Float -> restore.putFloat(key,value)
                is String -> restore.putString(key,value)
                is Set<*> -> @Suppress("UNCHECKED_CAST") restore.putStringSet(key,value as Set<String>)
            } }
            restore.commit()
            manager.cancelAll()
            repo.load()
            if (savedTimer != null) FocusAlarm.schedule(app, savedTimer) else FocusAlarm.cancel(app)
        }
    }
}
