package com.jdinohaila.studentgarden.platform

import android.Manifest
import android.app.*
import android.content.*
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.jdinohaila.studentgarden.*
import com.jdinohaila.studentgarden.domain.*
import java.time.*
import kotlinx.coroutines.*

/** Local reminders. Date-based practice is grouped once a day, never once per card. */
object StudyReminders {
    data class Category(val key: String, val id: Int, val title: Int, val body: Int)
    val categories = listOf(
        Category("tasks", 46, R.string.tasks, R.string.task_reminder_body),
        Category("agenda", 47, R.string.agenda, R.string.agenda_reminder_body),
        Category("cards", 48, R.string.cards, R.string.card_reminder_body),
        Category("habits", 49, R.string.habits, R.string.habit_reminder_body),
    )
    data class Entry(val category: String, val token: String, val at: Long)
    fun enabled(context: Context, key: String) = context.getSharedPreferences("settings", 0).getBoolean("${key}Reminders", key in setOf("tasks", "agenda"))
    fun channel(key: String) = "study_$key"
    fun allowed(context: Context, key: String? = null): Boolean =
        NotificationManagerCompat.from(context).areNotificationsEnabled() &&
            (Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) &&
            (key == null || context.getSystemService(NotificationManager::class.java).getNotificationChannel(channel(key))?.importance != NotificationManager.IMPORTANCE_NONE)

    /** Pure deadline calculation shared by scheduling and delivery, including fixed habit zones. */
    fun entries(state: GardenState, now: Long, minute: Int, zone: ZoneId, delivered: Map<String, Set<String>>): List<Entry> = buildList {
        state.tasks.filter { it.status != "done" && it.dueAt != null && it.dueAt >= now - ReminderTiming.STALE_LIMIT }.forEach {
            add(Entry("tasks", "${it.id}:${it.dueAt}", it.dueAt!!))
        }
        state.blocks.filter { !it.done && it.startsAt + it.minutes * 60_000L > now && it.startsAt >= now - ReminderTiming.STALE_LIMIT }.forEach {
            add(Entry("agenda", "${it.id}:${it.startsAt}", it.startsAt))
        }
        val time = LocalTime.of(minute.coerceIn(0, 1439) / 60, minute.coerceIn(0, 1439) % 60)
        val cards = state.cards.filter { it.stage < 4 }
        if (cards.isNotEmpty()) {
            var day = Instant.ofEpochMilli(ReminderTiming.studyTime(cards.minOf { it.dueAt }, now, minute, zone)).atZone(zone).toLocalDate()
            while (day.toString() in delivered["cards"].orEmpty()) day = day.plusDays(1)
            add(Entry("cards", day.toString(), day.atTime(time).atZone(zone).toInstant().toEpochMilli()))
        }
        state.habits.filter { !it.archived && it.days != 0 }.forEach { habit ->
            val habitZone = ZoneId.of(habit.zone)
            val earliest = LocalDate.parse(habit.startDay).atStartOfDay(habitZone).toInstant().toEpochMilli()
            var day = Instant.ofEpochMilli(ReminderTiming.studyTime(earliest, now, minute, habitZone)).atZone(habitZone).toLocalDate()
            val checked = state.checks.filter { it.habitId == habit.id }.map { it.day }.toSet()
            while (!StudyRules.scheduled(habit, day) || day.toString() in checked || "${habit.id}:$day" in delivered["habits"].orEmpty()) day = day.plusDays(1)
            add(Entry("habits", "${habit.id}:$day", day.atTime(time).atZone(habitZone).toInstant().toEpochMilli()))
        }
    }.filter { it.token !in delivered[it.category].orEmpty() }

    private fun pending(context: Context) = PendingIntent.getBroadcast(context, 3,
        Intent(context, StudyReminderReceiver::class.java).setAction("STUDY_DUE"),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    private fun delivered(context: Context) = categories.associate { category ->
        category.key to context.getSharedPreferences("settings", 0).getStringSet("${category.key}Delivered", emptySet()).orEmpty().toSet()
    }
    private fun entries(context: Context, state: GardenState, now: Long) = entries(state, now,
        context.getSharedPreferences("settings", 0).getInt("dailyReminderMinute", 9 * 60), ZoneId.systemDefault(), delivered(context))

    fun schedule(context: Context, state: GardenState, now: Long = System.currentTimeMillis()) {
        val alarm = context.getSystemService(AlarmManager::class.java)
        val manager = context.getSystemService(NotificationManager::class.java)
        val entries = entries(context, state, now)
        categories.forEach { category ->
            if (!enabled(context, category.key) || !hasDue(state, category.key, now)) manager.cancel(category.id)
        }
        val next = entries.filter { enabled(context, it.category) && allowed(context, it.category) }.minOfOrNull { it.at }
        if (next == null) { alarm.cancel(pending(context)); return }
        val trigger = maxOf(now + 1000, next)
        try {
            if (Build.VERSION.SDK_INT < 31 || alarm.canScheduleExactAlarms())
                alarm.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, trigger, pending(context))
            else alarm.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, trigger, pending(context))
        } catch (_: SecurityException) {
            alarm.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, trigger, pending(context))
        }
    }

    private fun hasDue(state: GardenState, key: String, now: Long) = when (key) {
        "tasks" -> state.tasks.any { it.status != "done" && (it.dueAt ?: Long.MAX_VALUE) <= now }
        "agenda" -> state.blocks.any { !it.done && it.startsAt <= now && it.startsAt + it.minutes * 60_000L > now }
        "cards" -> state.cards.any { StudyRules.due(it, now) }
        "habits" -> state.habits.any { h -> !h.archived && StudyRules.scheduled(h, StudyRules.today(h, Instant.ofEpochMilli(now))) && state.checks.none { it.habitId == h.id && it.day == StudyRules.today(h, Instant.ofEpochMilli(now)).toString() } }
        else -> false
    }

    fun openIntent(context: Context, destination: String, token: Long) = Intent(context, MainActivity::class.java)
        .putExtra("reminder_destination", destination).putExtra("reminder_request_token", token)
        .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)

    @Synchronized fun deliver(context: Context, state: GardenState, now: Long = System.currentTimeMillis()) {
        val prefs = context.getSharedPreferences("settings", 0)
        val delivered = delivered(context)
        val entries = entries(context, state, now)
        categories.forEach { category ->
            val due = entries.filter { it.category == category.key && it.at <= now }
            if (due.isNotEmpty() && enabled(context, category.key) && allowed(context, category.key) && hasDue(state, category.key, now)) {
                val manager = context.getSystemService(NotificationManager::class.java)
                manager.createNotificationChannel(NotificationChannel(channel(category.key), context.getString(category.title), NotificationManager.IMPORTANCE_DEFAULT))
                val open = PendingIntent.getActivity(context, category.id, openIntent(context, category.key, now), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
                try {
                    manager.notify(category.id, ReminderPresentation.notification(context, channel(category.key), category.key,
                        context.getString(category.title), context.resources.getQuantityString(R.plurals.reminder_due_items, due.size, due.size) + " · " + context.getString(category.body),
                        open, due.size, due.minOf { it.at }).build())
                    val live = when (category.key) {
                        "tasks", "agenda" -> if (category.key == "tasks") state.tasks.filter { it.status != "done" && it.dueAt != null }.map { "${it.id}:${it.dueAt}" }.toSet() else state.blocks.filter { !it.done }.map { "${it.id}:${it.startsAt}" }.toSet()
                        else -> null
                    }
                    val history = (delivered[category.key].orEmpty() + due.map { it.token })
                        .filter { token -> if (live != null) token in live else runCatching { LocalDate.parse(token.takeLast(10)) >= Instant.ofEpochMilli(now).atZone(ZoneId.systemDefault()).toLocalDate().minusDays(14) }.getOrDefault(false) }.toSet()
                    prefs.edit().putStringSet("${category.key}Delivered", history).commit()
                } catch (_: SecurityException) { /* A revocation never consumes a reminder. */ }
            }
        }
        schedule(context, state, now)
    }

    fun test(context: Context) {
        if (!allowed(context)) return
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel("reminder_test", context.getString(R.string.test_notification), NotificationManager.IMPORTANCE_DEFAULT))
        val open = PendingIntent.getActivity(context, 50, openIntent(context, "settings", System.currentTimeMillis()), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        try { manager.notify(50, ReminderPresentation.notification(context, "reminder_test", "lessons",
            context.getString(R.string.app_name), context.getString(R.string.test_notification_body), open).build()) } catch (_: SecurityException) { }
    }
}

class StudyReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val result = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val repo = (context.applicationContext as GardenApp).repository
                repo.load() // Also restores lesson alarms after reboot, upgrade and clock changes.
                if (intent.action == "STUDY_DUE") StudyReminders.deliver(context, repo.state.value)
                else repo.state.value.timer?.let { FocusAlarm.schedule(context, it) }
            } finally { result.finish() }
        }
    }
}
