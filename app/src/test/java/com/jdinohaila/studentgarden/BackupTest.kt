package com.jdinohaila.studentgarden

import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.jdinohaila.studentgarden.backup.GardenBackup
import com.jdinohaila.studentgarden.data.GardenRepository
import com.jdinohaila.studentgarden.domain.*
import com.jdinohaila.studentgarden.platform.PrivateMedia
import java.io.File
import java.util.zip.*
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = GardenApp::class)
class BackupTest {
    private lateinit var app: GardenApp
    private lateinit var repo: GardenRepository
    private lateinit var backup: GardenBackup

    @Before
    fun setup() = runBlocking {
        app = ApplicationProvider.getApplicationContext()
        app.deleteDatabase("garden.db")
        repo = GardenRepository(app)
        repo.load()
        backup = GardenBackup(app, repo, PrivateMedia(app))
    }

    @After
    fun close() {
        repo.database.close()
    }

    @Test
    fun backupRoundTripKeepsRichNotesAndDoesNotReplaceEdits() = runBlocking {
        val note = Note(title = "Lecture")
        repo.save(note, "<p><b>Recall</b></p>")
        val file = File(app.cacheDir, "roundtrip.zip")
        backup.export(Uri.fromFile(file))
        repo.save(note.copy(title = "Updated lecture"), "new body")
        val preview = backup.preview(Uri.fromFile(file))
        assertEquals(1, preview.manifest.records.size)
        backup.restore(preview)
        assertEquals("Updated lecture", repo.state.value.notes.single().title)
        assertEquals("new body", repo.body(note.id))
    }

    @Test
    fun pathTraversalIsRejectedBeforeWritingOutsideStaging() = runBlocking {
        val file = File(app.cacheDir, "evil.zip")
        ZipOutputStream(file.outputStream()).use {
            it.putNextEntry(ZipEntry("media/../../escape"))
            it.write("bad".toByteArray())
            it.closeEntry()
        }
        assertTrue(runCatching { backup.preview(Uri.fromFile(file)) }.isFailure)
        assertFalse(File(app.cacheDir, "escape").exists())
        assertTrue(repo.state.value.tasks.isEmpty())
    }

    @Test
    fun malformedBackupLeavesCurrentDataUntouched() = runBlocking {
        val task = Task(title = "Keep me")
        repo.save(task)
        val file = File(app.cacheDir, "broken.zip")
        ZipOutputStream(file.outputStream()).use {
            it.putNextEntry(ZipEntry("garden.json"))
            it.write("{}".toByteArray())
            it.closeEntry()
        }
        assertTrue(runCatching { backup.preview(Uri.fromFile(file)) }.isFailure)
        repo.load()
        assertEquals(task, repo.state.value.tasks.single())
    }

    @Test fun fullBackupRestoresRecordsSchedulesImagesAudioAndCheckHistory() = runBlocking {
        val media = PrivateMedia(app)
        val imageId = "${newId()}.png"
        val audioId = "${newId()}.m4a"
        media.file(imageId).writeBytes(byteArrayOf(1, 2, 3))
        media.file(audioId).writeBytes(byteArrayOf(4, 5, 6))
        val course = Course(title = "Biology")
        val deck = Deck(title = "Cells")
        val task = Task(title = "Revise", courseId = course.id)
        val habit = Habit(title = "Read")
        listOf(course, deck, task, habit,
            Note(title = "Lecture", courseId = course.id),
            StudyBlock(title = "Study", taskId = task.id, courseId = course.id, startsAt = 1234),
            Lesson(title = "Mitosis", stage = 1, dueAt = 2345),
            Flashcard(type = "image", imageId = imageId, audioId = audioId, answer = "Cell", deckId = deck.id, stage = 2, dueAt = 3456),
        ).forEach { repo.save(it, if (it is Note) "<p>Rich <b>content</b></p><table><tr><td>العائلة</td><td>Family &amp; friends</td></tr></table><p><i>After the table</i></p>" else null) }
        repo.check(habit, java.time.LocalDate.now())
        val expected = repo.snapshot()
        val file = File(app.cacheDir, "full.zip")
        backup.export(Uri.fromFile(file))
        repo.database.close()
        app.deleteDatabase("garden.db")
        media.file(imageId).delete(); media.file(audioId).delete()
        repo = GardenRepository(app); repo.load()
        backup = GardenBackup(app, repo, media)
        backup.restore(backup.preview(Uri.fromFile(file)))
        val restored = repo.snapshot()
        assertEquals(expected.first.toSet(), restored.first.toSet())
        assertEquals(expected.second, restored.second)
        assertEquals(expected.third, restored.third)
        assertArrayEquals(byteArrayOf(1, 2, 3), media.file(imageId).readBytes())
        assertArrayEquals(byteArrayOf(4, 5, 6), media.file(audioId).readBytes())
    }

    @Test fun missingMediaDoesNotProduceAnApparentlySuccessfulBackup() = runBlocking {
        repo.save(Flashcard(type = "image", imageId = "${newId()}.png", answer = "Cell"))
        val file = File(app.cacheDir, "missing-media.zip")
        file.writeText("Previous backup")
        assertTrue(runCatching { backup.export(Uri.fromFile(file)) }.isFailure)
        assertEquals("Previous backup", file.readText())
    }
}
