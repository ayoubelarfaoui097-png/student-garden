package com.jdinohaila.studentgarden

import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.jdinohaila.studentgarden.backup.GardenBackup
import com.jdinohaila.studentgarden.data.GardenRepository
import com.jdinohaila.studentgarden.domain.*
import com.jdinohaila.studentgarden.ui.recordingLevel
import com.jdinohaila.studentgarden.video.*
import java.io.File
import java.net.URLDecoder
import java.util.Base64
import java.util.zip.*
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
class AudioVideoDataTest {
    private lateinit var app: GardenApp
    private lateinit var repo: GardenRepository
    @Before fun setup() = runBlocking {
        app = ApplicationProvider.getApplicationContext()
        app.deleteDatabase("garden.db")
        repo = GardenRepository(app); repo.load()
    }
    @After fun close() { repo.database.close() }

    @Test fun audioNotesAndVideoTimestampSurviveACompleteBackupWithoutFlashcards() = runBlocking {
        val id = "${newId()}.m4a"
        val bytes = byteArrayOf(1, 7, 4, 9)
        app.media.file(id).writeBytes(bytes)
        val note = Note(title = "Lecture recording", media = listOf(id), videoId = "CMiPYHNNg28", videoPositionMillis = 123000)
        val html = "<p>Listen again</p><table><tr><td>Recall</td></tr></table>"
        repo.save(note, html)
        val uri = Uri.fromFile(File(app.cacheDir, "audio-notes.zip"))
        var backup = GardenBackup(app, repo, app.media)
        backup.export(uri)
        repo.database.close(); app.deleteDatabase("garden.db"); app.media.file(id).delete()
        repo = GardenRepository(app); repo.load(); backup = GardenBackup(app, repo, app.media)
        backup.restore(backup.preview(uri))
        assertEquals(note, repo.state.value.notes.single())
        assertEquals(html, repo.body(note.id))
        assertArrayEquals(bytes, app.media.file(id).readBytes())
    }
    @Test fun missingAudioDoesNotOverwriteAnExistingBackupAndInvalidReferencesAreRejected() = runBlocking {
        val note = Note(title = "Voice", media = listOf("${newId()}.m4a"))
        repo.save(note, "")
        val file = File(app.cacheDir, "voice-missing.zip").apply { writeText("Keep old backup") }
        val backup = GardenBackup(app, repo, app.media)
        assertTrue(runCatching { backup.export(Uri.fromFile(file)) }.isFailure)
        assertEquals("Keep old backup", file.readText())
        assertTrue(runCatching { repo.save(note.copy(media = listOf("../../secret.m4a"))) }.isFailure)
        val manifest = com.jdinohaila.studentgarden.backup.BackupManifest(records = listOf(note), notes = mapOf(note.id to ""), checks = emptyList())
        ZipOutputStream(file.outputStream()).use { it.putNextEntry(ZipEntry("garden.json")); it.write(repo.json.encodeToString(manifest).toByteArray()); it.closeEntry() }
        assertTrue(runCatching { backup.preview(Uri.fromFile(file)) }.isFailure)
        assertEquals(note, repo.state.value.notes.single())
    }
    @Test fun oldNotesDecodeWithNoAudioOrVideoSource() {
        val id = newId()
        val note = repo.json.decodeFromString<Note>("""{"id":"$id","title":"Original"}""")
        assertEquals(emptyList<String>(), note.media); assertNull(note.videoId); assertEquals(0L, note.videoPositionMillis)
    }
    @Test fun recordingLevelsFollowTheMicrophoneFromSilenceToLoudSpeech() {
        assertEquals(0f, recordingLevel(0))
        assertTrue(recordingLevel(100) < recordingLevel(1000))
        assertTrue(recordingLevel(1000) < recordingLevel(10000))
        assertEquals(1f, recordingLevel(32767)); assertEquals(1f, recordingLevel(Int.MAX_VALUE))
    }
    @Test fun filterParametersMatchYoutubeAndDoNotDropCombinedFilters() {
        assertEquals("8AEB", SearchFilters().params())
        assertEquals("EgIQAfABAQ%3D%3D", SearchFilters(type = SearchType.VIDEOS).params())
        assertEquals("EgIQA_ABAQ%3D%3D", SearchFilters(type = SearchType.PLAYLISTS).params())
        val encoded = SearchFilters(SearchType.VIDEOS, SearchDuration.LONG, SearchDate.WEEK, SearchSort.VIEWS).params()
        val bytes = Base64.getUrlDecoder().decode(URLDecoder.decode(encoded, "UTF-8"))
        assertArrayEquals(byteArrayOf(8, 3, 18, 6, 8, 3, 16, 1, 24, 2, 240.toByte(), 1, 1), bytes)
    }
    @Test fun playlistLinksKeepThePlaylistFromCombinedWatchUrlsAndRejectOtherHosts() {
        val list = "PL12345678901234567890123456789012"
        assertEquals(list, PlaylistLinks.id("https://www.youtube.com/watch?v=CMiPYHNNg28&list=$list&index=2"))
        assertEquals(list, PlaylistLinks.id("https://youtu.be/CMiPYHNNg28?list=$list"))
        assertEquals(list, PlaylistLinks.id(PlaylistLinks.url(list)))
        assertNull(PlaylistLinks.id("photosynthesis"))
        listOf("https://youtube.com.evil.test/playlist?list=$list", "file:///playlist?list=$list", "https://youtube.com@evil.test/playlist?list=$list").forEach { assertNull(PlaylistLinks.id(it)) }
    }
}
