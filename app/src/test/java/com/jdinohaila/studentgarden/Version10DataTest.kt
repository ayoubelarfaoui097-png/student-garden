package com.jdinohaila.studentgarden

import android.Manifest
import android.app.*
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.jdinohaila.studentgarden.backup.GardenBackup
import com.jdinohaila.studentgarden.data.GardenRepository
import com.jdinohaila.studentgarden.domain.*
import com.jdinohaila.studentgarden.platform.*
import com.jdinohaila.studentgarden.video.*
import java.io.File
import java.time.*
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.decodeFromString
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.*
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = GardenApp::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class Version10DataTest {
    private lateinit var app: GardenApp
    private lateinit var repo: GardenRepository
    private val now = Instant.parse("2026-10-22T18:00:00Z").toEpochMilli()
    @Before fun setup() = runBlocking {
        app = ApplicationProvider.getApplicationContext(); app.deleteDatabase("garden.db")
        app.getSharedPreferences("settings", 0).edit().clear().commit()
        Shadows.shadowOf(app).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        repo = GardenRepository(app); repo.load()
    }
    @After fun close() { repo.database.close() }
    @Test fun defaultSearchKeepsVideoRelevanceWithOnlyAFewCollectionsAndCreators() {
        val videos = (0..19).map { VideoHit(VideoItem("video$it", "Lesson $it", "Teacher", 600)) }
        val extras = listOf(PlaylistHit(PlaylistItem("PL12345678901234567890123456789012", "Course", "Teacher", 20)), ChannelHit(ChannelItem("https://www.youtube.com/@teacher", "Teacher")))
        val result = mixedSearch(videos, extras)
        assertEquals(22, result.size); assertEquals(videos, result.filterIsInstance<VideoHit>())
        assertTrue(result[4] is PlaylistHit); assertTrue(result[9] is ChannelHit)
        assertEquals("EgIQAvABAQ%3D%3D", SearchFilters(type = SearchType.CHANNELS).params())
    }
    @Test fun channelLinksAcceptHandlesAndCanonicalIdsWithoutAllowingOtherHosts() {
        assertEquals("https://www.youtube.com/@teacher", ChannelLinks.url("https://youtube.com/@teacher?view=1"))
        assertNotNull(ChannelLinks.url("https://www.youtube.com/channel/UC123456789"))
        listOf("file:///channel/test", "https://youtube.com.evil.test/@teacher", "https://youtube.com@evil.test/@teacher", "https://www.youtube.com/watch?v=123", "https://www.youtube.com:8443/@teacher").forEach { assertNull(ChannelLinks.url(it)) }
    }
    @Test fun capturedJpegAndVoiceRestoreWithTimestampAndRichText() = runBlocking {
        val bitmap = Bitmap.createBitmap(320, 180, Bitmap.Config.ARGB_8888).apply { eraseColor(android.graphics.Color.BLUE) }
        val imageId = app.media.saveFrame(bitmap); bitmap.recycle()
        val imageBytes = app.media.file(imageId).readBytes()
        val decoded = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
        assertEquals(320, decoded.width); assertEquals(180, decoded.height); decoded.recycle()
        val audioId = "${newId()}.m4a"; val audioBytes = byteArrayOf(1, 2, 3, 7)
        app.media.file(audioId).writeBytes(audioBytes)
        val note = Note(title = "Frame and voice", images = listOf(imageId), media = listOf(audioId), videoId = "CMiPYHNNg28", videoPositionMillis = 92000)
        repo.save(note, "<p><b>Recall</b></p>")
        val uri = Uri.fromFile(File(app.cacheDir, "frames.zip"))
        var backup = GardenBackup(app, repo, app.media); backup.export(uri)
        repo.database.close(); app.deleteDatabase("garden.db"); app.media.file(imageId).delete(); app.media.file(audioId).delete()
        repo = GardenRepository(app); repo.load(); backup = GardenBackup(app, repo, app.media)
        backup.restore(backup.preview(uri))
        assertEquals(note, repo.state.value.notes.single()); assertEquals("<p><b>Recall</b></p>", repo.body(note.id))
        assertArrayEquals(imageBytes, app.media.file(imageId).readBytes()); assertArrayEquals(audioBytes, app.media.file(audioId).readBytes())
        assertTrue(runCatching { repo.save(note.copy(images = listOf("../../frame.jpg"))) }.isFailure)
        val legacy = repo.json.decodeFromString<Note>("""{"id":"${newId()}","title":"Original"}""")
        assertTrue(legacy.images.isEmpty())
    }
    @Test fun dailyPracticeIsOptInAndMissedOrStaleItemsDoNotFireOnOpen() {
        assertFalse(StudyReminders.enabled(app, "cards")); assertFalse(StudyReminders.enabled(app, "habits"))
        assertTrue(StudyReminders.enabled(app, "tasks"))
        val state = GardenState(tasks = listOf(Task(title = "Expired", dueAt = now - 3 * 3600000)), cards = listOf(Flashcard(answer = "Recall", dueAt = now - 86400000)))
        StudyReminders.deliver(app, state, now)
        assertEquals(0, Shadows.shadowOf(app.getSystemService(NotificationManager::class.java)).size())
        val next = StudyReminders.entries(state, now, 540, ZoneId.of("UTC"), emptyMap())
        assertTrue(next.none { it.category == "tasks" })
        assertEquals(Instant.parse("2026-10-23T09:00:00Z").toEpochMilli(), next.single().at)
        assertEquals(Instant.parse("2026-10-23T09:00:00Z").toEpochMilli(), ReminderTiming.studyTime(now - 86400000, now, 540, ZoneId.of("UTC")))
        LessonReminder.deliver(app, listOf(Lesson(title = "Review", dueAt = now - 1)), now)
        assertNull(Shadows.shadowOf(app.getSystemService(NotificationManager::class.java)).getNotification(45))
    }
    @Test fun actualDeadlineProducesAColoredPrivateNotificationOnlyOnce() {
        val state = GardenState(tasks = listOf(Task(title = "Private title", dueAt = now - 1000)))
        StudyReminders.deliver(app, state, now)
        val manager = app.getSystemService(NotificationManager::class.java)
        val notification = Shadows.shadowOf(manager).getNotification(46)
        assertNotNull(notification); assertEquals(ReminderPresentation.color("tasks"), notification.color)
        assertNotNull(notification.getLargeIcon()); assertEquals(1, notification.number)
        assertFalse(notification.extras.getCharSequence("android.text").toString().contains("Private title"))
        manager.cancelAll(); StudyReminders.deliver(app, state, now + 1000)
        assertEquals(0, Shadows.shadowOf(manager).size())
    }
}
