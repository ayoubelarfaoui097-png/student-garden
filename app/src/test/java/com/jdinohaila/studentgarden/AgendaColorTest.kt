package com.jdinohaila.studentgarden

import android.content.ContentValues
import android.net.Uri
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.test.core.app.ApplicationProvider
import com.jdinohaila.studentgarden.backup.GardenBackup
import com.jdinohaila.studentgarden.data.GardenRepository
import com.jdinohaila.studentgarden.domain.StudyBlock
import com.jdinohaila.studentgarden.platform.PrivateMedia
import com.jdinohaila.studentgarden.ui.theme.*
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = GardenApp::class)
class AgendaColorTest {
    private lateinit var app: GardenApp
    private lateinit var repo: GardenRepository
    private lateinit var backup: GardenBackup

    private val legacyId = "416128ec-ad00-46ab-8e8e-2dcbb9271356"
    // A real version-one shape: no color field and no schema migration required.
    private val legacyRecord = """
        {"recordType":"com.jdinohaila.studentgarden.domain.StudyBlock",
         "id":"$legacyId","title":"Biology revision","courseId":null,"taskId":null,
         "startsAt":1893538800000,"minutes":45,"done":true,"source":"planner","createdAt":1234}
    """.trimIndent()

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

    private suspend fun reopenEmptyDatabase() {
        repo.database.close()
        app.deleteDatabase("garden.db")
        repo = GardenRepository(app)
        repo.load()
        backup = GardenBackup(app, repo, PrivateMedia(app))
    }

    @Test
    fun oldDatabaseRecordDefaultsToBlueAndPreservesItsSchedule() = runBlocking {
        repo.database.writableDatabase.insertOrThrow("records", null, ContentValues().apply {
            put("id", legacyId)
            put("kind", "block")
            put("data", legacyRecord)
            put("updated_at", 1234L)
        })
        repo.load()
        val block = repo.state.value.blocks.single()
        assertEquals("blue", block.color)
        assertEquals(1893538800000L, block.startsAt)
        assertEquals(45, block.minutes)
        assertTrue(block.done)
        assertEquals(1234L, block.createdAt)
        assertEquals("planner", block.source)
    }

    @Test
    fun customColorSurvivesEditingReloadAndTrashUndo() = runBlocking {
        val original = StudyBlock(title = "Read", startsAt = 1893538800000L,
            minutes = 45, color = "#9368C4")
        repo.save(original)
        val edited = original.copy(title = "Read chapter two", done = true)
        repo.save(edited)
        repo.load()
        assertEquals(edited, repo.state.value.blocks.single())
        repo.trash(original.id)
        assertTrue(repo.state.value.blocks.isEmpty())
        assertEquals(edited, repo.state.value.trash.single())
        repo.undoTrash(original.id)
        repo.load()
        assertEquals(edited, repo.state.value.blocks.single())
    }

    @Test
    fun customColorBackupRoundTripPreservesAgenda() = runBlocking {
        val block = StudyBlock(title = "Math practice", startsAt = 1893538800000L,
            minutes = 60, done = true, color = "#FC53B9")
        repo.save(block)
        val file = File(app.cacheDir, "agenda-color-roundtrip.zip")
        backup.export(Uri.fromFile(file))
        reopenEmptyDatabase()
        val preview = backup.preview(Uri.fromFile(file))
        assertEquals(2, preview.manifest.version)
        backup.restore(preview)
        repo.load()
        assertEquals(block, repo.state.value.blocks.single())
    }

    @Test
    fun oldVersionOneBackupRestoresUncoloredAgendaAsBlue() = runBlocking {
        val file = File(app.cacheDir, "legacy-agenda.zip")
        ZipOutputStream(file.outputStream()).use { zip ->
            zip.putNextEntry(ZipEntry("garden.json"))
            zip.write("""
                {"format":"student-garden-android","version":1,
                 "records":[$legacyRecord],"notes":{},"checks":[]}
            """.trimIndent().toByteArray())
            zip.closeEntry()
        }
        backup.restore(backup.preview(Uri.fromFile(file)))
        repo.load()
        val block = repo.state.value.blocks.single()
        assertEquals(legacyId, block.id)
        assertEquals("blue", block.color)
        assertEquals(1893538800000L, block.startsAt)
        assertEquals(45, block.minutes)
        assertTrue(block.done)
    }

    @Test
    fun hexInputAcceptsOnlySixDigitOpaqueColors() {
        assertEquals("#12ABEF", normalizedHexColor("  #12abef  "))
        assertEquals(Color(0xFF12ABEF), accent("#12abef"))
        listOf("", "blue", "#FFF", "123ABC", "#12345G", "#1234567", "#00123456")
            .forEach { assertNull("Should reject $it", normalizedHexColor(it)) }
    }

    @Test
    fun namedPaletteAndMalformedStoredColorHaveSafeFallback() {
        assertEquals(listOf("green", "blue", "gold", "coral", "teal", "cyan", "orange", "rose"), featureColors)
        assertEquals(Color(0xFF3895F5), accent("blue"))
        assertEquals(Color(0xFF20B66A), accent("green"))
        assertEquals(Color(0xFFF59B38), accent("orange"))
        assertEquals(accent("slate"), accent("#not-a-color"))
    }

    @Test
    fun agendaControlsRemainVisibleOnLightAndDarkSurfaces() {
        val colors = featureColors + listOf("#FFFFFF", "#000000", "#777777", "#FFFF00", "#000040")
        listOf(Color.White, Color(0xFF172130)).forEach { surface ->
            colors.forEach { name ->
                val background = accent(name).copy(alpha = .13f).compositeOver(surface)
                val marker = agendaAccent(name, background)
                assertTrue("$name marker must contrast with $surface", contrastRatio(marker, background) >= 3f)
                assertTrue("$name checkmark must be readable", contrastRatio(contrastingColor(marker), marker) >= 4.5f)
            }
        }
    }
}
