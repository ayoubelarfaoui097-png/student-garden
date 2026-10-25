package com.jdinohaila.studentgarden

import android.Manifest
import android.app.*
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import com.jdinohaila.studentgarden.domain.*
import com.jdinohaila.studentgarden.platform.*
import java.time.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.*
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = GardenApp::class)
class StudyRemindersTest {
    private lateinit var app: GardenApp
    private val zone = ZoneId.of("UTC")
    private val now = Instant.parse("2026-10-19T10:00:00Z").toEpochMilli()
    private val task = Task(title = "Private task", dueAt = now - 1)
    private val block = StudyBlock(title = "Private plan", startsAt = now - 1)
    private val card = Flashcard(answer = "Secret", dueAt = now - 86400000)
    private val habit = Habit(title = "Private habit", zone = "UTC", startDay = "2026-10-01")
    private val state get() = GardenState(tasks = listOf(task), blocks = listOf(block), cards = listOf(card), habits = listOf(habit))
    private val originalZone = java.util.TimeZone.getDefault()
    @After fun restoreZone() { java.util.TimeZone.setDefault(originalZone) }
    @Before fun setup() {
        app = ApplicationProvider.getApplicationContext()
        app.getSharedPreferences("settings", 0).edit().clear().putBoolean("cardsReminders", true).putBoolean("habitsReminders", true).putInt("dailyReminderMinute", 600).commit()
        shadowOf(app).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        java.util.TimeZone.setDefault(java.util.TimeZone.getTimeZone(zone))
    }
    @Test fun dueItemsPostFourPrivateNotificationsWithCorrectDestinations() {
        StudyReminders.deliver(app, state, now)
        val manager = shadowOf(app.getSystemService(NotificationManager::class.java))
        StudyReminders.categories.forEach {
            val notification = manager.getNotification(it.id)
            assertNotNull(it.key, notification)
            assertEquals(it.key, shadowOf(notification.contentIntent).savedIntent.getStringExtra("reminder_destination"))
            assertFalse(notification.extras.getCharSequence("android.text").toString().contains("Private"))
        }
    }
    @Test fun deliveryDoesNotRepeatOrForgetEarlierTaskTokens() {
        StudyReminders.deliver(app, state, now)
        val manager = app.getSystemService(NotificationManager::class.java)
        manager.cancelAll()
        StudyReminders.deliver(app, state, now + 1000)
        assertEquals(0, shadowOf(manager).size())
        val second = task.copy(id = newId(), dueAt = now + 2000)
        StudyReminders.deliver(app, state.copy(tasks = listOf(task, second)), now + 3000)
        manager.cancelAll()
        StudyReminders.deliver(app, state.copy(tasks = listOf(task, second)), now + 4000)
        assertEquals(0, shadowOf(manager).size())
    }
    @Test fun tomorrowRemindsAgainForUnfinishedDailyPractice() {
        StudyReminders.deliver(app, state, now)
        val entries = StudyReminders.entries(state, now, 540, zone, mapOf("cards" to setOf("2026-10-19"), "habits" to setOf("${habit.id}:2026-10-19")))
        assertEquals(Instant.parse("2026-10-20T09:00:00Z").toEpochMilli(), entries.single { it.category == "cards" }.at)
        assertEquals(Instant.parse("2026-10-20T09:00:00Z").toEpochMilli(), entries.single { it.category == "habits" }.at)
    }
    @Test fun completeArchivedExpiredAndTrashedItemsDoNotNotify() {
        val finished = state.copy(tasks = listOf(task.copy(status = "done")), blocks = listOf(block.copy(done = true)), cards = listOf(card.copy(stage = 4)), habits = listOf(habit.copy(archived = true)))
        StudyReminders.deliver(app, finished, now)
        assertEquals(0, shadowOf(app.getSystemService(NotificationManager::class.java)).size())
        assertTrue(StudyReminders.entries(GardenState(trash = listOf(task, block, card, habit)), now, 540, zone, emptyMap()).isEmpty())
        assertTrue(StudyReminders.entries(GardenState(blocks = listOf(block.copy(startsAt = now - 3600000))), now, 540, zone, emptyMap()).isEmpty())
    }
    @Test fun deniedOrDisabledChannelsConsumeNothingAndRecoverAfterGrant() {
        shadowOf(app).denyPermissions(Manifest.permission.POST_NOTIFICATIONS)
        StudyReminders.deliver(app, state, now)
        assertTrue(app.getSharedPreferences("settings", 0).getStringSet("tasksDelivered", emptySet())!!.isEmpty())
        assertTrue(shadowOf(app.getSystemService(AlarmManager::class.java)).scheduledAlarms.isEmpty())
        shadowOf(app).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        val manager = app.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel(StudyReminders.channel("tasks"), "Tasks", NotificationManager.IMPORTANCE_NONE))
        StudyReminders.deliver(app, state, now)
        assertNull(shadowOf(manager).getNotification(46))
        assertNotNull(shadowOf(manager).getNotification(48))
    }
    @Test fun categorySwitchCancelsAlarmAndPostedReminder() {
        val taskState = GardenState(tasks = listOf(task))
        StudyReminders.deliver(app, taskState, now)
        app.getSharedPreferences("settings", 0).edit().putBoolean("tasksReminders", false).commit()
        StudyReminders.schedule(app, taskState, now)
        assertNull(shadowOf(app.getSystemService(NotificationManager::class.java)).getNotification(46))
        assertTrue(shadowOf(app.getSystemService(AlarmManager::class.java)).scheduledAlarms.isEmpty())
    }
    @Test fun dueTimeEditsAndCompletionRescheduleAndCancel() {
        val next = task.copy(dueAt = now + 90000)
        StudyReminders.schedule(app, GardenState(tasks = listOf(next)), now)
        assertEquals(next.dueAt, shadowOf(app.getSystemService(AlarmManager::class.java)).scheduledAlarms.single().triggerAtTime)
        StudyReminders.schedule(app, GardenState(tasks = listOf(next.copy(status = "done"))), now)
        assertTrue(shadowOf(app.getSystemService(AlarmManager::class.java)).scheduledAlarms.isEmpty())
    }
    @Test fun habitsRespectWeekdaysChecksFutureStartAndTheirSavedZone() {
        val friday = habit.copy(days = 1 shl 4, startDay = "2026-10-23", zone = "Asia/Tokyo")
        var entries = StudyReminders.entries(GardenState(habits = listOf(friday)), now, 540, zone, emptyMap())
        assertEquals(Instant.parse("2026-10-23T00:00:00Z").toEpochMilli(), entries.single().at)
        entries = StudyReminders.entries(GardenState(habits = listOf(friday), checks = listOf(HabitCheck(friday.id, "2026-10-23"))), now, 540, zone, emptyMap())
        assertEquals(Instant.parse("2026-10-30T00:00:00Z").toEpochMilli(), entries.single().at)
        val dst = habit.copy(zone = "America/New_York")
        val autumn = Instant.parse("2026-11-01T04:00:00Z").toEpochMilli()
        assertEquals(Instant.parse("2026-11-01T14:00:00Z").toEpochMilli(), StudyReminders.entries(GardenState(habits = listOf(dst)), autumn, 540, zone, emptyMap()).single().at)
    }
    @Test fun learningIntervalsDoNotGenerateRepeatedDailyCardAlerts() {
        val later = card.copy(dueAt = now + 30000)
        val entry = StudyReminders.entries(GardenState(cards = listOf(later)), now, 540, zone, emptyMap()).single()
        assertEquals(Instant.parse("2026-10-20T09:00:00Z").toEpochMilli(), entry.at)
    }
    @Test fun bootUpgradeAndClockBroadcastsRebuildTheNextAlarmFromStorage() {
        kotlinx.coroutines.runBlocking {
            app.deleteDatabase("garden.db")
            app.repository.load()
            app.repository.save(task.copy(dueAt = System.currentTimeMillis() + 600000))
        }
        val alarm = app.getSystemService(AlarmManager::class.java)
        val receiver = StudyReminderReceiver()
        val filter = android.content.IntentFilter().apply { listOf(Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_MY_PACKAGE_REPLACED, Intent.ACTION_TIME_CHANGED, Intent.ACTION_TIMEZONE_CHANGED).forEach { addAction(it) } }
        androidx.core.content.ContextCompat.registerReceiver(app, receiver, filter, androidx.core.content.ContextCompat.RECEIVER_NOT_EXPORTED)
        listOf(Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_MY_PACKAGE_REPLACED, Intent.ACTION_TIME_CHANGED, Intent.ACTION_TIMEZONE_CHANGED).forEach { action ->
            shadowOf(alarm).scheduledAlarms.toList().forEach { it.operation?.let { pending -> alarm.cancel(pending) } }
            app.sendBroadcast(Intent(action).setPackage(app.packageName))
            shadowOf(android.os.Looper.getMainLooper()).idle()
            val until = System.currentTimeMillis() + 5000
            while (shadowOf(alarm).scheduledAlarms.isEmpty() && System.currentTimeMillis() < until) Thread.sleep(10)
            assertEquals(action, 1, shadowOf(alarm).scheduledAlarms.size)
        }
        app.unregisterReceiver(receiver)
    }

    @Test fun systemReceiversArePrivateAndHandleReschedulingEvents() {
        val info = app.packageManager.getReceiverInfo(android.content.ComponentName(app, StudyReminderReceiver::class.java), 0)
        assertFalse(info.exported)
        listOf(Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_MY_PACKAGE_REPLACED, Intent.ACTION_TIME_CHANGED, Intent.ACTION_TIMEZONE_CHANGED).forEach { action ->
            assertTrue(app.packageManager.queryBroadcastReceivers(Intent(action).setPackage(app.packageName), 0).any { it.activityInfo.name.endsWith("StudyReminderReceiver") })
        }
    }
}
