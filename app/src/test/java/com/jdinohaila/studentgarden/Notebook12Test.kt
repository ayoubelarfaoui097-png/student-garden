package com.jdinohaila.studentgarden

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import androidx.test.core.app.ApplicationProvider
import com.jdinohaila.studentgarden.data.GardenRepository
import com.jdinohaila.studentgarden.domain.*
import com.jdinohaila.studentgarden.export.*
import com.jdinohaila.studentgarden.video.*
import java.io.File
import java.util.zip.ZipFile
import javax.xml.parsers.DocumentBuilderFactory
import kotlinx.coroutines.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.*

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = GardenApp::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class Notebook12Test {
    private lateinit var app: GardenApp
    private lateinit var repo: GardenRepository
    private val video = "CMiPYHNNg28"
    @Before fun setup() = runBlocking {
        app = ApplicationProvider.getApplicationContext(); app.deleteDatabase("garden.db")
        repo = GardenRepository(app); repo.load()
    }
    @After fun close() { repo.database.close() }

    @Test fun legacyRichBodyAndDuplicateTimestampsHaveDistinctEditableEntries() {
        val a = newId(); val b = newId(); val frame = "${newId()}.jpg"
        val note = Note(title = "Cells", videoId = video, videoMoments = listOf(VideoMoment(a, 42000), VideoMoment(b, 42000, images = listOf(frame))), images = listOf(frame))
        val rich = "<table class=\"original\"><tr><td><b>Keep all formatting</b></td></tr></table>"
        val body = VideoNotebook.body(video, listOf(NotebookEntry(a,42000,rich)))
        val entries = VideoNotebook.entries(note, body)
        assertEquals(2,entries.size); assertEquals(rich,entries.first().html); assertEquals(listOf(frame),entries.last().images)
        assertEquals(body,VideoNotebook.body(video,entries))
    }
    @Test fun editsPreserveConcurrentAdditionsAndFrameUndoDoesNotReplaceWrittenText() = runBlocking {
        val a = Note(title="Cells",videoId=video,videoPositionMillis=1000)
        repo.appendVideoNote(a,"<p>First</p>")
        val id=repo.state.value.notes.single().id
        val original=VideoNotebook.entries(repo.state.value.notes.single(),repo.body(id)).single()
        repo.appendVideoNote(Note(title="Cells",videoId=video,videoPositionMillis=2000),"<p>Concurrent</p>")
        repo.editVideoEntry(id,original,"Revised <idea> & العربية")
        val image="${newId()}.jpg"
        repo.appendVideoNote(Note(title="Cells",videoId=video,videoPositionMillis=3000,images=listOf(image)))
        var note=repo.state.value.notes.single()
        val frame=VideoNotebook.entries(note,repo.body(id)).single { it.images.isNotEmpty() }
        repo.removeVideoImage(id,frame,image); assertTrue(repo.state.value.notes.single().images.isEmpty())
        repo.restoreVideoEntry(id,frame)
        note=repo.state.value.notes.single()
        assertEquals(listOf(image),note.images)
        assertTrue(repo.body(id).contains("Concurrent")); assertTrue(repo.body(id).contains("&lt;idea&gt;"))
        assertEquals(3,VideoNotebook.entries(note,repo.body(id)).size)
        assertTrue(runCatching { repo.editVideoEntry(id,original,"Stale overwrite") }.isFailure)
        Unit
    }
    @Test fun nativeExportsContainUnicodeTextAndActualImagesWithValidXmlParts() {
        val frame="${newId()}.jpg"
        val bitmap=Bitmap.createBitmap(960,540,Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(Color.rgb(230,242,251))
        Canvas(bitmap).drawText("Mitosis: one cell becomes two",50f,120f,Paint(Paint.ANTI_ALIAS_FLAG).apply { color=Color.rgb(0,82,204); textSize=44f })
        app.media.file(frame).outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG,95,it) }; bitmap.recycle()
        val note=ExportNote("Cell division | انقسام الخلية",video,listOf(
            NotebookEntry(newId(),15000,"<p>DNA is copied before division.</p><p>تتضاعف المادة الوراثية قبل الانقسام.</p>"),
            NotebookEntry(newId(),31000,images=listOf(frame)),
            NotebookEntry(newId(),45000,"<p>"+"Each new cell receives a complete set of chromosomes. ".repeat(50)+"</p>")))
        val output=File("build/qa/exports").apply { mkdirs() }
        // Robolectric has no native PdfDocument implementation. PDF rendering is
        // verified on Android by NoteExportDeviceTest with the real PdfRenderer.
        listOf(NoteFile.WORD, NoteFile.SLIDES).forEach { format ->
            val file=File(output,"notebook.${format.extension}")
            NoteExport(app,app.media).write(note,format,file)
            assertTrue(file.length()>500)
            if(format!=NoteFile.PDF) ZipFile(file).use { zip ->
                zip.entries().asSequence().filter { it.name.endsWith(".xml") || it.name.endsWith(".rels") }.forEach { part ->
                    zip.getInputStream(part).use { DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(it) }
                }
                assertTrue(zip.entries().asSequence().any { it.name.endsWith(".jpg") })
                val doc=zip.getInputStream(zip.getEntry(if(format==NoteFile.WORD) "word/document.xml" else "ppt/slides/slide1.xml")).bufferedReader().readText()
                assertTrue(doc.contains("DNA")); assertTrue(doc.contains("تتضاعف"))
                if (format == NoteFile.WORD) {
                    val english = doc.indexOf("DNA is copied")
                    val arabic = doc.indexOf("تتضاعف")
                    assertTrue(doc.indexOf("</w:p>", english) < arabic)
                    assertFalse(doc.substring(doc.lastIndexOf("<w:p>", english), english).contains("<w:rtl/>"))
                }
            }
        }
    }
    @Test fun slidesSplitLongTextWithoutDroppingCharactersOrSplittingSurrogates() {
        val text="DNA 🧬 العربية and chromosomes. ".repeat(100)
        val chunks=NoteExport.splitSlideText(text)
        assertEquals(text,chunks.joinToString("")); assertTrue(chunks.size>1)
        assertTrue(chunks.all { it.length<=451 && !Character.isHighSurrogate(it.last()) })
        val lines = (1..100).joinToString("\n") { "Key point $it" }
        val pages = NoteExport.splitSlideText(lines)
        assertEquals(lines, pages.joinToString(""))
        assertTrue(pages.size > 5)
        assertTrue(pages.all { it.count { char -> char == '\n' } < 15 })
    }
}
