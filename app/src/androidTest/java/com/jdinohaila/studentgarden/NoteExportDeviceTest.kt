package com.jdinohaila.studentgarden

import android.graphics.*
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.jdinohaila.studentgarden.domain.*
import com.jdinohaila.studentgarden.export.*
import com.jdinohaila.studentgarden.video.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** Native PDF generation/rendering needs Android; Robolectric has no PdfDocument backend. */
@RunWith(AndroidJUnit4::class)
class NoteExportDeviceTest {
    @Test fun exportsNativePdfWithArabicImagesAndMultiplePagesAndSeedsAnIsolatedNotebook() = runBlocking {
        val app=ApplicationProvider.getApplicationContext<GardenApp>()
        check(app.packageName.endsWith(".preview"))
        val frame="${newId()}.jpg"
        val bitmap=Bitmap.createBitmap(1280,720,Bitmap.Config.ARGB_8888)
        val canvas=Canvas(bitmap)
        canvas.drawColor(Color.rgb(222,239,249))
        val paint=Paint(Paint.ANTI_ALIAS_FLAG).apply { color=Color.rgb(0,82,204); textSize=58f }
        canvas.drawText("Mitosis: one cell becomes two",65f,125f,paint)
        paint.style=Paint.Style.STROKE;paint.strokeWidth=12f
        canvas.drawCircle(390f,410f,140f,paint);canvas.drawCircle(880f,410f,140f,paint)
        app.media.file(frame).outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG,95,it) };bitmap.recycle()
        val note=ExportNote("Cell division | انقسام الخلية","CMiPYHNNg28",listOf(
            NotebookEntry(newId(),15000,"<p>DNA is copied before division.</p><p>تتضاعف المادة الوراثية قبل الانقسام.</p>"),
            NotebookEntry(newId(),31000,images=listOf(frame)),
            NotebookEntry(newId(),45000,"<p>"+"Each new cell receives a complete set of chromosomes. ".repeat(100)+"</p>"),
            NotebookEntry(newId(),60000,"<p>"+(1..80).joinToString("<br>") { "Key point $it" }+"</p>")))
        val output=File(app.getExternalFilesDir(null),"exports-012").apply { mkdirs() }
        NoteFile.entries.forEach { format -> NoteExport(app,app.media).write(note,format,File(output,"notebook.${format.extension}")) }
        val descriptor=ParcelFileDescriptor.open(File(output,"notebook.pdf"),ParcelFileDescriptor.MODE_READ_ONLY)
        val pdf=PdfRenderer(descriptor)
        try {
            assertTrue(pdf.pageCount>=2)
            val page=pdf.openPage(0)
            val rendered=Bitmap.createBitmap(595,842,Bitmap.Config.ARGB_8888)
            try {
                rendered.eraseColor(Color.WHITE)
                page.render(rendered,null,null,PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                val pixels=IntArray(rendered.width*rendered.height)
                rendered.getPixels(pixels,0,rendered.width,0,0,rendered.width,rendered.height)
                assertTrue(pixels.count { Color.red(it)<200 || Color.green(it)<200 || Color.blue(it)<200 }>1000)
            } finally { rendered.recycle();page.close() }
        } finally { pdf.close();descriptor.close() }
        // Only fictional records in this task's new AVD; no database clearing.
        app.repository.load()
        app.repository.appendVideoNote(Note(title="Cell division",videoId="CMiPYHNNg28",videoPositionMillis=15000),"<p>DNA is copied before division.</p><p>تتضاعف المادة الوراثية قبل الانقسام.</p>")
        app.repository.appendVideoNote(Note(title="Cell division",videoId="CMiPYHNNg28",videoPositionMillis=31000,images=listOf(frame)))
        app.repository.appendVideoNote(Note(title="Cell division",videoId="CMiPYHNNg28",videoPositionMillis=45000),"<p>Each new cell receives a complete set of chromosomes.</p>")
        app.repository.save(Note(title="Study plan"),"<p>Revise the chapter and review flashcards.</p>")
        Unit
    }
}
