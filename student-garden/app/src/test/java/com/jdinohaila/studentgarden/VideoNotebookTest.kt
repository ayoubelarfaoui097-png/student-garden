package com.jdinohaila.studentgarden

import android.content.ContentValues
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.jdinohaila.studentgarden.backup.GardenBackup
import com.jdinohaila.studentgarden.data.GardenRepository
import com.jdinohaila.studentgarden.domain.*
import java.io.File
import kotlinx.coroutines.*
import kotlinx.serialization.encodeToString
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = GardenApp::class)
class VideoNotebookTest {
    private lateinit var app: GardenApp
    private lateinit var repo: GardenRepository
    private val video = "CMiPYHNNg28"
    @Before fun setup() = runBlocking {
        app = ApplicationProvider.getApplicationContext()
        app.deleteDatabase("garden.db"); repo = GardenRepository(app); repo.load()
    }
    @After fun close() { repo.database.close() }
    private fun entry(at: Long, text: String = "", audio: List<String> = emptyList(), images: List<String> = emptyList()) =
        Note(title = "Biology", preview = text, media = audio, images = images, videoId = video, videoPositionMillis = at)

    @Test fun concurrentTextVoiceAndFramesAppendToOneNoteAndRetryIsIdempotent() = runBlocking {
        val first = entry(1000, "First")
        repo.appendVideoNote(first, "<p>First</p>")
        val audio = "${newId()}.m4a"; val image = "${newId()}.jpg"
        val entries = (1..12).map { entry(it * 1000L, "Point $it") } + entry(42000, audio = listOf(audio)) + entry(43000, images = listOf(image))
        coroutineScope { entries.map { async(Dispatchers.Default) { repo.appendVideoNote(it, if (it.preview.isBlank()) "" else "<p>${it.preview}</p>") } }.awaitAll() }
        val note = repo.state.value.notes.single()
        assertEquals(first.id, note.id); assertEquals(15, note.videoMoments.size)
        assertEquals(listOf(audio), note.media); assertEquals(listOf(image), note.images)
        assertEquals(42000L, note.videoMoments.single { audio in it.media }.positionMillis)
        assertEquals(43000L, note.videoMoments.single { image in it.images }.positionMillis)
        val body = repo.body(note.id)
        entries.filter { it.preview.isNotBlank() }.forEach { assertTrue(body.contains("<p>${it.preview}</p>")) }
        repo.appendVideoNote(entries.first(), "<p>Point 1</p>")
        assertEquals(note, repo.state.value.notes.single()); assertEquals(body, repo.body(note.id))
        Unit
    }
    @Test fun upgradeMergesLegacyNotesPreservingRichTablesMediaAndOriginalTimes() = runBlocking {
        val audio = "${newId()}.m4a"; val image = "${newId()}.jpg"
        val text = entry(10000, "Photosynthesis").copy(createdAt = 1)
        val voice = entry(42000, audio = listOf(audio)).copy(createdAt = 2)
        val frame = entry(45000, images = listOf(image)).copy(createdAt = 3)
        val html = "<p><b>Photosynthesis</b></p><table><tr><td style=\"color:red\">Water</td></tr></table>"
        // Seed the previous version's records without invoking the new save normalization.
        listOf(text, voice, frame).forEach { note ->
            repo.database.writableDatabase.insertOrThrow("records", null, ContentValues().apply {
                put("id", note.id); put("kind", "note"); put("data", repo.json.encodeToString<GardenRecord>(note)); put("updated_at", note.createdAt)
            })
        }
        repo.database.writableDatabase.insertOrThrow("note_contents", null, ContentValues().apply { put("id", text.id); put("html", html) })
        repo.load()
        val merged = repo.state.value.notes.single()
        assertEquals(text.id, merged.id); assertEquals(listOf(10000L, 42000L, 45000L), merged.videoMoments.map { it.positionMillis })
        assertEquals(listOf(audio), merged.media); assertEquals(listOf(image), merged.images)
        assertTrue(repo.body(merged.id).contains(html))
        val before = repo.body(merged.id)
        repo.load(); assertEquals(before, repo.body(merged.id))
        // Restoring an old backup again must not duplicate annotations or text.
        repo.merge(listOf(text, voice, frame), mapOf(text.id to html), emptyList())
        assertEquals(3, repo.state.value.notes.single().videoMoments.size)
        assertEquals(before, repo.body(merged.id))
        Unit
    }
    @Test fun combinedNotebookAndItsMediaRoundTripThroughBackup() = runBlocking {
        val audio = "${newId()}.m4a"; val image = "${newId()}.jpg"
        app.media.file(audio).writeBytes(byteArrayOf(1,2)); app.media.file(image).writeBytes(byteArrayOf(3,4))
        repo.appendVideoNote(entry(3000, "Cells"), "<p>Cells</p>")
        repo.appendVideoNote(entry(4000, audio = listOf(audio)))
        repo.appendVideoNote(entry(5000, images = listOf(image)))
        val before = repo.state.value.notes.single(); val body = repo.body(before.id)
        val uri = Uri.fromFile(File(app.cacheDir, "combined-video.zip"))
        GardenBackup(app, repo, app.media).export(uri)
        repo.database.close(); app.deleteDatabase("garden.db"); app.media.file(audio).delete(); app.media.file(image).delete()
        repo = GardenRepository(app); repo.load()
        GardenBackup(app, repo, app.media).let { it.restore(it.preview(uri)) }
        assertEquals(before, repo.state.value.notes.single()); assertEquals(body, repo.body(before.id))
        assertArrayEquals(byteArrayOf(1,2), app.media.file(audio).readBytes())
        assertArrayEquals(byteArrayOf(3,4), app.media.file(image).readBytes())
        Unit
    }
    @Test fun staleEditorCannotOverwriteNewVideoAdditionsAndForeignMediaMarksAreRejected() = runBlocking {
        repo.appendVideoNote(entry(1000, "Old"), "<p>Old</p>")
        val old = repo.state.value.notes.single()
        repo.appendVideoNote(entry(2000, "New"), "<p>New</p>")
        assertTrue(runCatching { repo.save(old, "<p>Overwrite</p>") }.isFailure)
        assertTrue(repo.body(old.id).contains("New"))
        val invalid = old.copy(videoMoments = listOf(VideoMoment(positionMillis = 1000, media = listOf("${newId()}.m4a"))))
        assertTrue(runCatching { repo.validate(invalid) }.isFailure)
        Unit
    }
}
