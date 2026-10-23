package com.jdinohaila.studentgarden

import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.jdinohaila.studentgarden.backup.GardenBackup
import com.jdinohaila.studentgarden.data.GardenRepository
import com.jdinohaila.studentgarden.domain.*
import com.jdinohaila.studentgarden.platform.PrivateMedia
import com.jdinohaila.studentgarden.video.VideoLinks
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = GardenApp::class)
class VideoAndLessonTest {
    private lateinit var app: GardenApp
    private lateinit var repo: GardenRepository
    @Before fun setup() = runBlocking {
        app = ApplicationProvider.getApplicationContext()
        app.deleteDatabase("garden.db")
        repo = GardenRepository(app); repo.load()
    }
    @After fun close() { repo.database.close() }
    @Test fun acceptsYoutubeLinksWithoutAllowingOtherHostsOrLocalFiles() {
        val id = "dQw4w9WgXcQ"
        listOf(id, "https://youtu.be/$id?t=12", "https://www.youtube.com/watch?v=$id&list=123", "https://m.youtube.com/embed/$id", "https://youtube.com/shorts/$id").forEach { assertEquals(it, id, VideoLinks.id(it)) }
        listOf("file:///etc/passwd", "https://youtube.com.evil.test/watch?v=$id", "https://youtube.com@evil.test/watch?v=$id", "https://youtu.be/$id/extra", "https://youtube.com/watch?v=invalid", "javascript:alert(1)", "https://evil.test/$id").forEach { assertNull(it, VideoLinks.id(it)) }
    }
    @Test fun legacyLessonLoadsWithCyanAndKeepsItsReviewSchedule() = runBlocking {
        val lesson = Lesson(title = "Before videos", stage = 1, dueAt = 123456789)
        val json = repo.json.encodeToString<GardenRecord>(lesson).replace(",\"color\":\"cyan\"", "")
        val loaded = repo.json.decodeFromString<GardenRecord>(json) as Lesson
        assertEquals("cyan", loaded.color)
        assertEquals(lesson.dueAt, loaded.dueAt)
        repo.save(loaded.copy(color = "#123ABC")); repo.load()
        assertEquals("#123ABC", repo.state.value.lessons.single().color)
        assertEquals(lesson.dueAt, repo.state.value.lessons.single().dueAt)
        assertTrue(runCatching { repo.save(loaded.copy(color = "#NOPE")) }.isFailure)
    }
    @Test fun savedVideosDeduplicateAndResumeSurvivesReloadAndBackup() = runBlocking {
        repo.saveVideo("dQw4w9WgXcQ", "A public lesson", "Teacher", 100, 12000)
        repo.saveVideo("dQw4w9WgXcQ", "A public lesson", "Teacher", 100, 24000)
        repo.videoPosition("dQw4w9WgXcQ", 31000); repo.load()
        assertEquals(1, repo.state.value.videos.size)
        val original = repo.state.value.videos.single()
        assertEquals(31000L, original.positionMillis)
        repo.save(Lesson(title = "Biology", color = "#AA1234"))
        val backup = GardenBackup(app, repo, PrivateMedia(app))
        val uri = Uri.fromFile(File(app.cacheDir, "videos-v2.zip"))
        backup.export(uri)
        val preview = backup.preview(uri)
        assertEquals(2, preview.manifest.version)
        assertEquals(original, preview.manifest.records.filterIsInstance<LearningVideo>().single())
        assertEquals("#AA1234", preview.manifest.records.filterIsInstance<Lesson>().single().color)
        repo.trash(original.id); repo.undoTrash(original.id)
        assertEquals(original, repo.state.value.videos.single())
        preview.staged.deleteRecursively(); Unit
    }
    @Test fun stillImportsVersionOneBackupsWithOriginalLessonDates() = runBlocking {
        val lesson = Lesson(title = "Legacy", dueAt = 123456)
        val json = repo.json.encodeToString<GardenRecord>(lesson).replace(",\"color\":\"cyan\"", "")
        val file = File(app.cacheDir, "lesson-v1.zip")
        ZipOutputStream(file.outputStream()).use { out ->
            out.putNextEntry(ZipEntry("garden.json"))
            out.write("""{"format":"student-garden-android","version":1,"records":[$json],"notes":{},"checks":[]}""".toByteArray())
            out.closeEntry()
        }
        val backup = GardenBackup(app, repo, PrivateMedia(app))
        backup.restore(backup.preview(Uri.fromFile(file)))
        assertEquals(lesson.dueAt, repo.state.value.lessons.single().dueAt)
        assertEquals("cyan", repo.state.value.lessons.single().color)
    }
}
