package com.jdinohaila.studentgarden.platform

import android.Manifest
import android.app.*
import android.content.*
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.net.Uri
import android.os.*
import android.provider.Settings
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.jdinohaila.studentgarden.*
import com.jdinohaila.studentgarden.domain.*
import kotlinx.coroutines.*

object FocusAlarm {
    fun boot(context: Context) =
        Settings.Global.getInt(context.contentResolver, Settings.Global.BOOT_COUNT, 0)

    fun pending(context: Context, id: String) =
        PendingIntent.getBroadcast(
            context,
            0,
            Intent(context, FocusReceiver::class.java).apply {
                action = "FOCUS_FINISHED"
                putExtra("id", id)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    fun schedule(context: Context, timer: FocusTimer) {
        if (timer.endsAt == null || timer.finished) return
        val alarm = context.getSystemService(AlarmManager::class.java)
        val trigger =
            SystemClock.elapsedRealtime() +
                StudyRules.remaining(
                    timer,
                    System.currentTimeMillis(),
                    SystemClock.elapsedRealtime(),
                    boot(context),
                )
        val pi = pending(context, timer.id)
        try {
            if (Build.VERSION.SDK_INT < 31 || alarm.canScheduleExactAlarms())
                alarm.setExactAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, trigger, pi)
            else alarm.setAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, trigger, pi)
        } catch (_: SecurityException) {
            // Permission can be revoked between the check and scheduling.
            alarm.setAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, trigger, pi)
        }
    }

    fun cancel(context: Context) {
        context.getSystemService(AlarmManager::class.java).cancel(pending(context, ""))
    }

    fun notify(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java)
        val prefs = context.getSharedPreferences("settings", 0)
        val sound =
            if (prefs.getBoolean("sound", true))
                Uri.parse(
                    "android.resource://${context.packageName}/raw/${context.resources.getResourceEntryName(R.raw.focus_bell)}"
                )
            else null
        val channel = if (sound == null) "focus_silent" else "focus_bell"
        manager.createNotificationChannel(
            NotificationChannel(
                    channel,
                    context.getString(R.string.focus),
                    NotificationManager.IMPORTANCE_HIGH,
                )
                .apply {
                    setSound(
                        sound,
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_ALARM)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build(),
                    )
                    enableVibration(sound != null)
                }
        )
        if (
            Build.VERSION.SDK_INT >= 33 &&
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS,
                ) != PackageManager.PERMISSION_GRANTED
        )
            return
        val intent =
            PendingIntent.getActivity(
                context,
                1,
                StudyReminders.openIntent(context, "focus", System.currentTimeMillis()),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
        try {
        manager.notify(
            44,
            ReminderPresentation.notification(context, channel, "focus", context.getString(R.string.focus_finished), context.getString(R.string.take_break), intent)
                .setCategory(NotificationCompat.CATEGORY_ALARM)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .build(),
        )
        } catch (_: SecurityException) {
            // Notification permission can be revoked after the runtime check.
        }
    }
}

class FocusReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val repository = (context.applicationContext as GardenApp).repository
                repository.load()
                if (
                    intent.action == Intent.ACTION_BOOT_COMPLETED ||
                        intent.action ==
                            AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED
                )
                    repository.state.value.timer?.let { FocusAlarm.schedule(context, it) }
                else
                    intent.getStringExtra("id")?.let {
                        if (repository.finishTimer(it)) FocusAlarm.notify(context)
                    }
            } finally {
                pending.finish()
            }
        }
    }
}
