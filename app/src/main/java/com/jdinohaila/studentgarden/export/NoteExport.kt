// SPDX-License-Identifier: GPL-3.0-or-later
package com.jdinohaila.studentgarden.export

import android.content.Context
import android.graphics.*
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.text.*
import com.jdinohaila.studentgarden.R
import com.jdinohaila.studentgarden.domain.Note
import com.jdinohaila.studentgarden.platform.PrivateMedia
import com.jdinohaila.studentgarden.video.*
import java.io.*
import java.util.zip.*
import kotlinx.coroutines.*

// Open document formats, written locally. No cloud conversion or document SDK.
enum class NoteFile(val extension: String, val mime: String, val label: String) {
    PDF("pdf", "application/pdf", "PDF"),
    WORD("docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document", "Word (.docx)"),
    SLIDES("pptx", "application/vnd.openxmlformats-officedocument.presentationml.presentation", "Slides (.pptx)")
}

data class ExportNote(val title: String, val videoId: String?, val entries: List<NotebookEntry>) {
    companion object {
        fun from(note: Note, html: String) = ExportNote(note.title, note.videoId,
            if (note.videoId != null) VideoNotebook.entries(note, html)
            else listOf(NotebookEntry(note.id, 0, html, note.images, note.media)))
    }
}

class NoteExport(private val context: Context, private val media: PrivateMedia) {
    suspend fun save(note: ExportNote, format: NoteFile, uri: Uri) = withContext(Dispatchers.IO) {
        val staged = File.createTempFile("note-export-", ".${format.extension}", context.cacheDir)
        try {
            write(note, format, staged)
            context.contentResolver.openOutputStream(uri, "wt")!!.use { output -> staged.inputStream().use { it.copyTo(output) } }
        } finally { staged.delete() }
    }

    fun write(note: ExportNote, format: NoteFile, file: File) {
        // Check every reference before opening the destination; missing frames are not silently omitted.
        note.entries.flatMap { it.images }.distinct().forEach { require(media.file(it).isFile) { "Missing frame" } }
        when (format) { NoteFile.PDF -> pdf(note, file); NoteFile.WORD -> word(note, file); NoteFile.SLIDES -> slides(note, file) }
    }

    private fun plain(html: String) = Html.fromHtml(html, Html.FROM_HTML_MODE_LEGACY).toString().trim()
    private fun source(note: ExportNote, entry: NotebookEntry? = null) = note.videoId?.let {
        "https://www.youtube.com/watch?v=$it" + if (entry == null) "" else "&t=${entry.positionMillis / 1000}s"
    }.orEmpty()
    private fun bitmap(id: String): Bitmap {
        val path = media.file(id).absolutePath
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, bounds)
        require(bounds.outWidth > 0 && bounds.outHeight > 0) { "Unreadable frame" }
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / sample > 1800) sample *= 2
        return BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = sample }) ?: error("Unreadable frame")
    }

    private fun pdf(note: ExportNote, file: File) {
        val document = PdfDocument()
        try {
            var page: PdfDocument.Page? = null
            var number = 0
            var y = 40f
            val width = 515
            fun finish() {
                page?.let {
                    val footer = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.DKGRAY; textSize = 9f }
                    it.canvas.drawText(number.toString(), 555f, 812f, footer)
                    document.finishPage(it)
                }
            }
            fun next() { finish(); number++; page = document.startPage(PdfDocument.PageInfo.Builder(595, 842, number).create()); y = 40f }
            fun text(value: String, size: Float, color: Int = Color.rgb(28, 30, 33)) {
                if (value.isBlank()) return
                val arabic = value.any { it in '\u0600'..'\u06ff' }
                val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                    textSize = size; this.color = color
                    typeface = context.resources.getFont(if (arabic) R.font.noto_arabic_regular else R.font.source_sans_regular)
                }
                val layout = StaticLayout.Builder.obtain(value, 0, value.length, paint, width)
                    .setTextDirection(TextDirectionHeuristics.FIRSTSTRONG_LTR).setIncludePad(false).setLineSpacing(3f, 1f).build()
                var first = 0
                while (first < layout.lineCount) {
                    if (y + layout.getLineBottom(first) - layout.getLineTop(first) > 790) next()
                    var last = first
                    while (last + 1 < layout.lineCount && y + layout.getLineBottom(last + 1) - layout.getLineTop(first) <= 790) last++
                    val height = layout.getLineBottom(last) - layout.getLineTop(first)
                    page!!.canvas.save()
                    page!!.canvas.clipRect(40f, y, 555f, y + height)
                    page!!.canvas.translate(40f, y - layout.getLineTop(first))
                    layout.draw(page!!.canvas); page!!.canvas.restore()
                    y += height + 10
                    first = last + 1
                }
            }
            next(); text(note.title, 23f); text(source(note), 10f, Color.rgb(0, 82, 204))
            note.entries.forEach { entry ->
                if (note.videoId != null) { if (y > 740) next(); text(VideoNotebook.time(entry.positionMillis), 12f, Color.rgb(0, 82, 204)) }
                text(plain(entry.html), 13f)
                entry.images.forEach { id ->
                    val image = bitmap(id)
                    try {
                        val scale = minOf(width.toFloat() / image.width, 640f / image.height, 1f)
                        val w = image.width * scale; val h = image.height * scale
                        if (y + h > 790) next()
                        page!!.canvas.drawBitmap(image, null, RectF(40f, y, 40f + w, y + h), Paint(Paint.ANTI_ALIAS_FLAG))
                        y += h + 18
                    } finally { image.recycle() }
                }
                y += 10
            }
            finish(); file.outputStream().use(document::writeTo)
        } finally { document.close() }
    }

    private fun xml(value: String): String = value.filter { it >= ' ' || it == '\n' || it == '\t' || it == '\r' }
        .replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;").replace("'", "&apos;")
    private fun ZipOutputStream.part(path: String, value: String) {
        putNextEntry(ZipEntry(path)); write(("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>" + value).toByteArray(Charsets.UTF_8)); closeEntry()
    }
    private fun ZipOutputStream.frame(path: String, id: String) {
        val image = bitmap(id)
        try { putNextEntry(ZipEntry(path)); check(image.compress(Bitmap.CompressFormat.JPEG, 92, this)); closeEntry() }
        finally { image.recycle() }
    }
    private val relationships = "http://schemas.openxmlformats.org/package/2006/relationships"
    private val office = "http://schemas.openxmlformats.org/officeDocument/2006/relationships"
    private fun rel(id: String, type: String, target: String, external: Boolean = false) =
        "<Relationship Id=\"$id\" Type=\"$office/$type\" Target=\"${xml(target)}\"${if(external) " TargetMode=\"External\"" else ""}/>"
    private fun types(overrides: String) = "<Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\"><Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/><Default Extension=\"xml\" ContentType=\"application/xml\"/><Default Extension=\"jpg\" ContentType=\"image/jpeg\"/>$overrides</Types>"
    private fun override(path: String, type: String) = "<Override PartName=\"/$path\" ContentType=\"application/vnd.openxmlformats-officedocument.$type+xml\"/>"

    private fun paragraph(value: String, size: Int = 24, bold: Boolean = false, blue: Boolean = false): String {
        val rtl = TextDirectionHeuristics.FIRSTSTRONG_LTR.isRtl(value, 0, value.length)
        return "<w:p><w:pPr><w:spacing w:after=\"160\"/>${if(rtl) "<w:bidi/>" else ""}</w:pPr><w:r><w:rPr><w:rFonts w:ascii=\"Arial\" w:hAnsi=\"Arial\" w:cs=\"Arial\"/><w:sz w:val=\"$size\"/><w:szCs w:val=\"$size\"/>${if(bold) "<w:b/>" else ""}${if(blue) "<w:color w:val=\"0052CC\"/>" else ""}${if(rtl) "<w:rtl/>" else ""}</w:rPr>" +
            value.split('\n').joinToString("<w:br/>") { "<w:t xml:space=\"preserve\">${xml(it)}</w:t>" } + "</w:r></w:p>"
    }
    private fun word(note: ExportNote, file: File) {
        val body = StringBuilder(paragraph(note.title, 36, bold = true))
        val rels = StringBuilder()
        if (note.videoId != null) { rels.append(rel("source", "hyperlink", source(note), true)); body.append("<w:p><w:hyperlink r:id=\"source\"><w:r><w:rPr><w:color w:val=\"0052CC\"/></w:rPr><w:t>${xml(source(note))}</w:t></w:r></w:hyperlink></w:p>") }
        val frames = mutableListOf<String>()
        note.entries.forEach { entry ->
            if (note.videoId != null) body.append(paragraph(VideoNotebook.time(entry.positionMillis), blue = true))
            plain(entry.html).split('\n').filter { it.isNotBlank() }.forEach { body.append(paragraph(it)) }
            entry.images.forEach { id ->
                frames += id
                val index = frames.size
                val image = bitmap(id)
                val scale = minOf(5_400_000.0 / image.width, 8_000_000.0 / image.height)
                val cx = (image.width * scale).toLong(); val cy = (image.height * scale).toLong(); image.recycle()
                rels.append(rel("img$index", "image", "media/frame$index.jpg"))
                body.append("""<w:p><w:r><w:drawing><wp:inline><wp:extent cx="$cx" cy="$cy"/><wp:docPr id="$index" name="Frame $index"/><a:graphic><a:graphicData uri="http://schemas.openxmlformats.org/drawingml/2006/picture"><pic:pic><pic:nvPicPr><pic:cNvPr id="$index" name="Frame $index"/><pic:cNvPicPr/></pic:nvPicPr><pic:blipFill><a:blip r:embed="img$index"/><a:stretch><a:fillRect/></a:stretch></pic:blipFill><pic:spPr><a:xfrm><a:off x="0" y="0"/><a:ext cx="$cx" cy="$cy"/></a:xfrm><a:prstGeom prst="rect"><a:avLst/></a:prstGeom></pic:spPr></pic:pic></a:graphicData></a:graphic></wp:inline></w:drawing></w:r></w:p>""")
            }
        }
        ZipOutputStream(file.outputStream().buffered()).use { zip ->
            zip.part("[Content_Types].xml", types(override("word/document.xml", "wordprocessingml.document.main")))
            zip.part("_rels/.rels", "<Relationships xmlns=\"$relationships\">${rel("document", "officeDocument", "word/document.xml")}</Relationships>")
            zip.part("word/_rels/document.xml.rels", "<Relationships xmlns=\"$relationships\">$rels</Relationships>")
            zip.part("word/document.xml", """<w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main" xmlns:r="$office" xmlns:wp="http://schemas.openxmlformats.org/drawingml/2006/wordprocessingDrawing" xmlns:a="http://schemas.openxmlformats.org/drawingml/2006/main" xmlns:pic="http://schemas.openxmlformats.org/drawingml/2006/picture"><w:body>$body<w:sectPr><w:pgSz w:w="11906" w:h="16838"/><w:pgMar w:top="1000" w:right="1000" w:bottom="1000" w:left="1000"/></w:sectPr></w:body></w:document>""")
            frames.forEachIndexed { i, id -> zip.frame("word/media/frame${i+1}.jpg", id) }
        }
    }

    private data class Slide(val text: String, val at: Long, val image: String? = null)
    private fun slides(note: ExportNote, file: File) {
        val slides = note.entries.flatMap { entry ->
            val text = plain(entry.html)
            splitSlideText(text).map { Slide(it, entry.positionMillis) } + entry.images.map { Slide("", entry.positionMillis, it) }
        }.ifEmpty { listOf(Slide("", 0)) }
        val p = "http://schemas.openxmlformats.org/presentationml/2006/main"
        val a = "http://schemas.openxmlformats.org/drawingml/2006/main"
        val group = "<p:nvGrpSpPr><p:cNvPr id=\"1\" name=\"\"/><p:cNvGrpSpPr/><p:nvPr/></p:nvGrpSpPr><p:grpSpPr><a:xfrm><a:off x=\"0\" y=\"0\"/><a:ext cx=\"0\" cy=\"0\"/><a:chOff x=\"0\" y=\"0\"/><a:chExt cx=\"0\" cy=\"0\"/></a:xfrm></p:grpSpPr>"
        fun box(id: Int, text: String, y: Long, height: Long, size: Int, color: String = "1C1E21") = """<p:sp><p:nvSpPr><p:cNvPr id="$id" name="Text $id"/><p:cNvSpPr txBox="1"/><p:nvPr/></p:nvSpPr><p:spPr><a:xfrm><a:off x="650000" y="$y"/><a:ext cx="10800000" cy="$height"/></a:xfrm><a:prstGeom prst="rect"><a:avLst/></a:prstGeom><a:noFill/></p:spPr><p:txBody><a:bodyPr wrap="square"/><a:lstStyle/>${text.split('\n').joinToString("") { line -> "<a:p><a:pPr${if(TextDirectionHeuristics.FIRSTSTRONG_LTR.isRtl(line, 0, line.length)) " rtl=\"1\" algn=\"r\"" else ""}/><a:r><a:rPr lang=\"en-US\" sz=\"$size\"><a:solidFill><a:srgbClr val=\"$color\"/></a:solidFill><a:latin typeface=\"Arial\"/><a:cs typeface=\"Arial\"/></a:rPr><a:t>${xml(line)}</a:t></a:r><a:endParaRPr sz=\"$size\"/></a:p>" }}</p:txBody></p:sp>"""
        ZipOutputStream(file.outputStream().buffered()).use { zip ->
            zip.part("[Content_Types].xml", types(override("ppt/presentation.xml", "presentationml.presentation.main") + override("ppt/slideMasters/slideMaster1.xml", "presentationml.slideMaster") + override("ppt/slideLayouts/slideLayout1.xml", "presentationml.slideLayout") + slides.indices.joinToString("") { override("ppt/slides/slide${it+1}.xml", "presentationml.slide") }))
            zip.part("_rels/.rels", "<Relationships xmlns=\"$relationships\">${rel("presentation", "officeDocument", "ppt/presentation.xml")}</Relationships>")
            zip.part("ppt/presentation.xml", """<p:presentation xmlns:p="$p" xmlns:r="$office"><p:sldMasterIdLst><p:sldMasterId id="2147483648" r:id="master"/></p:sldMasterIdLst><p:sldIdLst>${slides.indices.joinToString("") { "<p:sldId id=\"${256+it}\" r:id=\"slide${it+1}\"/>" }}</p:sldIdLst><p:sldSz cx="12192000" cy="6858000"/><p:notesSz cx="6858000" cy="9144000"/></p:presentation>""")
            zip.part("ppt/_rels/presentation.xml.rels", "<Relationships xmlns=\"$relationships\">${rel("master", "slideMaster", "slideMasters/slideMaster1.xml")}${slides.indices.joinToString("") { rel("slide${it+1}", "slide", "slides/slide${it+1}.xml") }}</Relationships>")
            zip.part("ppt/slideMasters/slideMaster1.xml", """<p:sldMaster xmlns:p="$p" xmlns:a="$a" xmlns:r="$office"><p:cSld><p:spTree>$group</p:spTree></p:cSld><p:clrMap accent1="accent1" accent2="accent2" accent3="accent3" accent4="accent4" accent5="accent5" accent6="accent6" bg1="lt1" bg2="lt2" folHlink="folHlink" hlink="hlink" tx1="dk1" tx2="dk2"/><p:sldLayoutIdLst><p:sldLayoutId id="2147483649" r:id="layout"/></p:sldLayoutIdLst><p:txStyles><p:titleStyle/><p:bodyStyle/><p:otherStyle/></p:txStyles></p:sldMaster>""")
            zip.part("ppt/slideMasters/_rels/slideMaster1.xml.rels", "<Relationships xmlns=\"$relationships\">${rel("layout", "slideLayout", "../slideLayouts/slideLayout1.xml")}</Relationships>")
            zip.part("ppt/slideLayouts/slideLayout1.xml", """<p:sldLayout xmlns:p="$p" xmlns:a="$a" xmlns:r="$office" type="blank" preserve="1"><p:cSld name="Blank"><p:spTree>$group</p:spTree></p:cSld><p:clrMapOvr><a:masterClrMapping/></p:clrMapOvr></p:sldLayout>""")
            zip.part("ppt/slideLayouts/_rels/slideLayout1.xml.rels", "<Relationships xmlns=\"$relationships\">${rel("master", "slideMaster", "../slideMasters/slideMaster1.xml")}</Relationships>")
            slides.forEachIndexed { index, slide ->
                val image = slide.image?.let(::bitmap)
                val picture = if (image != null) {
                    val scale = minOf(10_800_000.0 / image.width, 4_450_000.0 / image.height)
                    val cx = (image.width * scale).toLong(); val cy = (image.height * scale).toLong(); image.recycle()
                    """<p:pic><p:nvPicPr><p:cNvPr id="5" name="Frame"/><p:cNvPicPr/><p:nvPr/></p:nvPicPr><p:blipFill><a:blip r:embed="frame"/><a:stretch><a:fillRect/></a:stretch></p:blipFill><p:spPr><a:xfrm><a:off x="${(12192000-cx)/2}" y="1850000"/><a:ext cx="$cx" cy="$cy"/></a:xfrm><a:prstGeom prst="rect"><a:avLst/></a:prstGeom></p:spPr></p:pic>"""
                } else ""
                val title = if (note.title.length > 95) note.title.take(92) + "…" else note.title
                zip.part("ppt/slides/slide${index+1}.xml", """<p:sld xmlns:p="$p" xmlns:a="$a" xmlns:r="$office"><p:cSld><p:bg><p:bgPr><a:solidFill><a:srgbClr val="FFFFFF"/></a:solidFill><a:effectLst/></p:bgPr></p:bg><p:spTree>$group${box(2,title,300000,900000,2600)}${box(3,if(note.videoId!=null) VideoNotebook.time(slide.at) else "",1250000,400000,1400,"0052CC")}${box(4,slide.text,1900000,4400000,2200)}$picture</p:spTree></p:cSld><p:clrMapOvr><a:masterClrMapping/></p:clrMapOvr></p:sld>""")
                zip.part("ppt/slides/_rels/slide${index+1}.xml.rels", "<Relationships xmlns=\"$relationships\">${rel("layout","slideLayout","../slideLayouts/slideLayout1.xml")}${if(slide.image!=null) rel("frame","image","../media/frame${index+1}.jpg") else ""}</Relationships>")
                if (slide.image != null) zip.frame("ppt/media/frame${index+1}.jpg", slide.image)
            }
        }
    }

    companion object {
        fun splitSlideText(text: String): List<String> = buildList {
            var remaining = text
            val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 22f }
            while (remaining.isNotEmpty()) {
                var cut = minOf(450, remaining.length)
                if (Character.isHighSurrogate(remaining[cut-1])) cut--
                // Leave room for Office font differences. Character limits alone
                // do not bound the height of notes containing many line breaks.
                val layout = StaticLayout.Builder.obtain(remaining, 0, cut, paint, 800)
                    .setIncludePad(true).setLineSpacing(2f, 1f).build()
                val last = (0 until layout.lineCount).lastOrNull { layout.getLineBottom(it) <= 300 } ?: 0
                cut = minOf(cut, layout.getLineEnd(last)).coerceAtLeast(1)
                if (cut < remaining.length) {
                    val space = remaining.lastIndexOfAny(charArrayOf(' ', '\n'), cut)
                    if (space > cut / 2 && space < cut) cut = space + 1
                    if (Character.isHighSurrogate(remaining[cut-1])) cut--
                }
                add(remaining.take(cut)); remaining = remaining.drop(cut)
            }
        }
    }
}
