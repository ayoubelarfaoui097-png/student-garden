package com.jdinohaila.studentgarden.platform

import android.app.PendingIntent
import android.content.Context
import android.graphics.*
import androidx.core.app.NotificationCompat
import com.jdinohaila.studentgarden.R
import java.time.*

object ReminderTiming {
    const val STALE_LIMIT = 2 * 60 * 60_000L
    const val PRACTICE_GRACE = 15 * 60_000L
    /** Missed daily practice waits for the next chosen time instead of firing on app open. */
    fun studyTime(dueAt: Long, now: Long, minute: Int, zone: ZoneId): Long {
        val time = LocalTime.of(minute.coerceIn(0, 1439) / 60, minute.coerceIn(0, 1439) % 60)
        val due = Instant.ofEpochMilli(dueAt).atZone(zone)
        var day = maxOf(Instant.ofEpochMilli(now).atZone(zone).toLocalDate(), due.toLocalDate())
        if (day == due.toLocalDate() && due.toLocalTime() > time) day = day.plusDays(1)
        var at = day.atTime(time).atZone(zone).toInstant().toEpochMilli()
        if (at < now - PRACTICE_GRACE) at = day.plusDays(1).atTime(time).atZone(zone).toInstant().toEpochMilli()
        return at
    }
}

object ReminderPresentation {
    fun color(key: String) = when (key) {
        "tasks" -> 0xFFC23F34.toInt(); "agenda" -> 0xFF1769AA.toInt()
        "cards" -> 0xFFC56414.toInt(); "habits" -> 0xFF148048.toInt()
        "lessons" -> 0xFF087A8F.toInt(); "focus" -> 0xFF087D70.toInt()
        else -> 0xFF0052CC.toInt()
    }
    fun artwork(key: String): Bitmap {
        val bitmap = Bitmap.createBitmap(96, 96, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = color(key) }
        canvas.drawRoundRect(4f, 4f, 92f, 92f, 20f, 20f, paint)
        paint.color = Color.WHITE; paint.style = Paint.Style.STROKE; paint.strokeWidth = 5f; paint.strokeCap = Paint.Cap.ROUND; paint.strokeJoin = Paint.Join.ROUND
        when (key) {
            "tasks" -> { canvas.drawRoundRect(26f, 24f, 70f, 74f, 5f, 5f, paint); canvas.drawPath(Path().apply { moveTo(35f, 49f); lineTo(44f, 58f); lineTo(61f, 39f) }, paint) }
            "agenda" -> { canvas.drawRoundRect(23f, 28f, 73f, 73f, 5f, 5f, paint); canvas.drawLine(24f, 42f, 72f, 42f, paint); canvas.drawLine(35f, 22f, 35f, 33f, paint); canvas.drawLine(61f, 22f, 61f, 33f, paint); canvas.drawLine(36f, 55f, 58f, 55f, paint) }
            "cards" -> { canvas.drawRoundRect(23f, 22f, 65f, 64f, 4f, 4f, paint); canvas.drawRoundRect(32f, 33f, 75f, 76f, 4f, 4f, paint); canvas.drawLine(44f, 47f, 63f, 47f, paint); canvas.drawLine(44f, 58f, 57f, 58f, paint) }
            "habits" -> { canvas.drawPath(Path().apply { moveTo(29f, 68f); cubicTo(18f, 32f, 49f, 23f, 72f, 23f); cubicTo(74f, 58f, 53f, 75f, 29f, 68f); moveTo(27f, 75f); lineTo(60f, 38f) }, paint) }
            "lessons" -> { canvas.drawPath(Path().apply { moveTo(48f, 31f); quadTo(34f, 21f, 21f, 28f); lineTo(21f, 69f); quadTo(35f, 62f, 48f, 73f); quadTo(61f, 62f, 75f, 69f); lineTo(75f, 28f); quadTo(61f, 21f, 48f, 31f); lineTo(48f, 73f) }, paint) }
            else -> { canvas.drawCircle(48f, 50f, 25f, paint); canvas.drawLine(48f, 33f, 48f, 51f, paint); canvas.drawLine(48f, 51f, 61f, 58f, paint); canvas.drawLine(41f, 16f, 55f, 16f, paint) }
        }
        return bitmap
    }
    fun notification(context: Context, channel: String, key: String, title: String, body: String,
        open: PendingIntent, count: Int = 0, at: Long = System.currentTimeMillis()): NotificationCompat.Builder =
        NotificationCompat.Builder(context, channel).setSmallIcon(R.drawable.ic_notification)
            .setColor(color(key)).setLargeIcon(artwork(key)).setContentTitle(title).setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body)).setContentIntent(open).setAutoCancel(true)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE).setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setOnlyAlertOnce(true).setWhen(at).setShowWhen(true).setNumber(count)
}
