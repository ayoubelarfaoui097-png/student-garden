package com.jdinohaila.studentgarden.backup

import android.content.Context
import android.net.Uri
import com.jdinohaila.studentgarden.data.GardenRepository
import com.jdinohaila.studentgarden.domain.*
import com.jdinohaila.studentgarden.platform.PrivateMedia
import java.io.*
import java.util.zip.*
import kotlinx.coroutines.*
import kotlinx.serialization.*
import kotlinx.serialization.Serializable

@Serializable
data class BackupManifest(
    val format: String = "student-garden-android",
    val version: Int = 2,
    val records: List<GardenRecord>,
    val notes: Map<String, String>,
    val checks: List<HabitCheck>,
)

data class RestorePreview(val manifest: BackupManifest, val staged: File, val media: List<String>)

class GardenBackup(
    private val context: Context,
    private val repo: GardenRepository,
    private val media: PrivateMedia,
) {
    private fun mediaIds(records: List<GardenRecord>) = records.flatMap {
        when (it) {
            is Flashcard -> listOfNotNull(it.imageId, it.audioId)
            is Note -> it.media + it.images
            else -> emptyList()
        }
    }.distinct()
    suspend fun export(uri: Uri) =
        withContext(Dispatchers.IO) {
            val (records, notes, checks) = repo.snapshot()
            val manifest = BackupManifest(records = records, notes = notes, checks = checks)
            val files = mediaIds(records)
            val manifestBytes = repo.json.encodeToString(manifest).toByteArray()
            require(manifestBytes.size <= 20_000_000 && records.size <= 20000 && checks.size <= 100000 && files.size <= 5000) {
                "Backup exceeds import limits"
            }
            files.forEach { require(media.file(it).isFile) { "A media file is missing; backup was not written" } }
            require(manifestBytes.size.toLong() + files.sumOf { media.file(it).length() } <= 100_000_000) {
                "Backup exceeds 100 MB"
            }
            context.contentResolver.openOutputStream(uri, "wt")!!.use { stream ->
                ZipOutputStream(BufferedOutputStream(stream)).use { zip ->
                    zip.putNextEntry(ZipEntry("garden.json"))
                    zip.write(manifestBytes)
                    zip.closeEntry()
                    files.forEach { id ->
                        val file = media.file(id)
                        if (file.isFile) {
                            zip.putNextEntry(ZipEntry("media/$id"))
                            file.inputStream().use { it.copyTo(zip) }
                            zip.closeEntry()
                        }
                    }
                }
            }
        }

    suspend fun preview(uri: Uri): RestorePreview =
        withContext(Dispatchers.IO) {
            val staged = File(context.cacheDir, "restore-${newId()}").apply { mkdirs() }
            var total = 0L
            var entries = 0
            var manifest: BackupManifest? = null
            val files = mutableListOf<String>()
            val seen = mutableSetOf<String>()
            try {
                context.contentResolver.openInputStream(uri)!!.use { input ->
                    ZipInputStream(BufferedInputStream(input)).use { zip ->
                        while (true) {
                            val entry = zip.nextEntry ?: break
                            require(
                                ++entries <= 5001 && !entry.isDirectory && seen.add(entry.name)
                            ) {
                                "Invalid backup"
                            }
                            val limit = if (entry.name == "garden.json") 20_000_000 else 10_000_000
                            val dest =
                                if (entry.name == "garden.json") File(staged, "garden.json")
                                else {
                                    require(
                                        entry.name.startsWith("media/") &&
                                            entry.name.count { it == '/' } == 1
                                    ) {
                                        "Invalid backup path"
                                    }
                                    val id = entry.name.removePrefix("media/")
                                    media.file(id)
                                    files.add(id)
                                    File(staged, id)
                                }
                            dest.outputStream().use { out ->
                                val bytes = ByteArray(8192)
                                var size = 0
                                while (true) {
                                    val n = zip.read(bytes)
                                    if (n < 0) break
                                    size += n
                                    total += n
                                    require(size <= limit && total <= 100_000_000) {
                                        "Backup exceeds size limit"
                                    }
                                    out.write(bytes, 0, n)
                                }
                            }
                            zip.closeEntry()
                        }
                    }
                }
                val parsed =
                    repo.json.decodeFromString<BackupManifest>(
                        File(staged, "garden.json").readText()
                    )
                require(
                    parsed.format == "student-garden-android" &&
                        parsed.version in 1..2 &&
                        parsed.records.size <= 20000 &&
                        parsed.checks.size <= 100000 &&
                        parsed.records.map { it.id }.distinct().size == parsed.records.size
                ) {
                    "Unsupported backup"
                }
                parsed.records.forEach(repo::validate)
                val noteIds = parsed.records.filterIsInstance<Note>().map { it.id }.toSet()
                require(parsed.notes.keys.all { it in noteIds })
                parsed.notes.values.forEach { require(it.length <= 1_000_000) }
                val habits = parsed.records.filterIsInstance<Habit>().associateBy { it.id }
                parsed.checks.forEach {
                    val habit = habits[it.habitId] ?: error("Habit is missing")
                    val day = java.time.LocalDate.parse(it.day)
                    require(
                        day >= java.time.LocalDate.parse(habit.startDay) &&
                            day <= StudyRules.today(habit)
                    )
                }
                mediaIds(parsed.records)
                    .forEach {
                        require(it in files || media.file(it).exists()) { "Media is missing" }
                    }
                RestorePreview(parsed, staged, files)
            } catch (e: Exception) {
                staged.deleteRecursively()
                throw IllegalArgumentException(
                    context.getString(com.jdinohaila.studentgarden.R.string.invalid_backup),
                    e,
                )
            }
        }

    suspend fun restore(preview: RestorePreview) =
        withContext(Dispatchers.IO) {
            // UUID names and merge-only records prevent an import from overwriting existing work.
            preview.media.forEach { id ->
                val dest = media.file(id)
                if (!dest.exists()) File(preview.staged, id).copyTo(dest, false)
            }
            repo.merge(preview.manifest.records, preview.manifest.notes, preview.manifest.checks)
            preview.staged.deleteRecursively()
        }
}
