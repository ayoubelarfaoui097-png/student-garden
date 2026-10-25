package com.jdinohaila.studentgarden.platform

import android.Manifest
import android.app.*
import android.content.*
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.jdinohaila.studentgarden.GardenApp
import com.jdinohaila.studentgarden.MainActivity
import com.jdinohaila.studentgarden.R
import com.jdinohaila.studentgarden.domain.Lesson
import kotlinx.coroutines.*

/** One local alarm for the next unannounced lesson deadline, with no network service. */
object LessonReminder {
    private const val CHANNEL = "lesson_reviews"
    const val NOTIFICATION_ID = 45

    fun token(lesson: Lesson) = "${lesson.id}:${lesson.dueAt}"

    fun pending(lessons: List<Lesson>, delivered: Set<String>) =
        lessons.filter { it.stage < 3 && it.dueAt != null && token(it) !in delivered }

    private fun alarmIntent(context: Context) = PendingIntent.getBroadcast(
        context, 2,
        Intent(context, LessonReminderReceiver::class.java).setAction("LESSONS_DUE"),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    fun allowed(context: Context): Boolean =
        NotificationManagerCompat.from(context).areNotificationsEnabled() &&
            (Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(
                context, Manifest.permission.POST_NOTIFICATIONS,
            ) == PackageManager.PERMISSION_GRANTED) &&
            context.getSystemService(NotificationManager::class.java)
                .getNotificationChannel(CHANNEL)?.importance != NotificationManager.IMPORTANCE_NONE

    fun schedule(context: Context, lessons: List<Lesson>, now: Long = System.currentTimeMillis()) {
        val alarm = context.getSystemService(AlarmManager::class.java)
        val pi = alarmIntent(context)
        val prefs = context.getSharedPreferences("settings", 0)
        val liveTokens = lessons.filter { it.stage < 3 && it.dueAt != null }.map(::token).toSet()
        val delivered = prefs.getStringSet("lessonDelivered", emptySet()).orEmpty().intersect(liveTokens)
        prefs.edit().putStringSet("lessonDelivered", delivered).apply()
        val unannounced = pending(lessons, delivered)
        if (lessons.none { it.stage < 3 && (it.dueAt ?: Long.MAX_VALUE) <= now })
            context.getSystemService(NotificationManager::class.java).cancel(NOTIFICATION_ID)
        if (!prefs.getBoolean("lessonReminders", false) || !allowed(context) || unannounced.isEmpty()) {
            alarm.cancel(pi)
            if (!prefs.getBoolean("lessonReminders", false))
                context.getSystemService(NotificationManager::class.java).cancel(NOTIFICATION_ID)
            return
        }
        val minute = prefs.getInt("dailyReminderMinute", 540)
        val target = unannounced.minOf { ReminderTiming.studyTime(it.dueAt!!, now, minute, java.time.ZoneId.systemDefault()) }
        val trigger = maxOf(now + 1000L, target)
        try {
            if (Build.VERSION.SDK_INT < 31 || alarm.canScheduleExactAlarms())
                alarm.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, trigger, pi)
            else alarm.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, trigger, pi)
        } catch (_: SecurityException) {
            alarm.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, trigger, pi)
        }
    }

    @Synchronized fun deliver(context: Context, lessons: List<Lesson>, now: Long = System.currentTimeMillis()) {
        val prefs = context.getSharedPreferences("settings", 0)
        if (!prefs.getBoolean("lessonReminders", false) || !allowed(context)) {
            schedule(context, lessons, now)
            return
        }
        val delivered = prefs.getStringSet("lessonDelivered", emptySet()).orEmpty().toSet()
        val minute = prefs.getInt("dailyReminderMinute", 540)
        val due = pending(lessons, delivered).filter { it.dueAt!! <= now && ReminderTiming.studyTime(it.dueAt, now, minute, java.time.ZoneId.systemDefault()) <= now }
        if (due.isNotEmpty()) {
            val manager = context.getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(NotificationChannel(
                CHANNEL, context.getString(R.string.lessons), NotificationManager.IMPORTANCE_DEFAULT,
            ))
            val open = PendingIntent.getActivity(
                context, 2,
                Intent(context, MainActivity::class.java).putExtra("lesson_request_token", now)
                    .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            try {
                manager.notify(NOTIFICATION_ID, ReminderPresentation.notification(context, CHANNEL, "lessons",
                    context.getString(R.string.lesson_reminder_title), context.resources.getQuantityString(R.plurals.reminder_due_items, due.size, due.size) + " · " + context.getString(R.string.lesson_reminder_body),
                    open, due.size, now).build())
                // Copy the set: SharedPreferences owns the returned instance.
                prefs.edit().putStringSet("lessonDelivered", delivered + due.map(::token)).commit()
            } catch (_: SecurityException) {
                // Permission may have been revoked between the check and posting.
            }
        }
        schedule(context, lessons, now)
    }
}

class LessonReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val result = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val repo = (context.applicationContext as GardenApp).repository
                repo.load()
                LessonReminder.deliver(context, repo.state.value.lessons)
            } finally {
                result.finish()
            }
        }
    }
}
